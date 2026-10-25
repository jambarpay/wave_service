# Paiements PayDunya en sandbox

Le service conserve son nom Kubernetes `wave-service` et ses routes
`/api/v1/wave` pour être compatible avec la passerelle. Il utilise désormais
PayDunya HTTP/JSON avec redirection vers la page du fournisseur.

Documentation officielle : https://developers.paydunya.com/doc/FR/http_json

## Configuration

Dans le dépôt GitHub de wave_service, modifier **Settings → Secrets and variables
→ Actions → WAVE_SERVICE_ENV**. Conserver les variables Eureka, les ports et
le JWT existants, et remplacer le bloc KkiaPay par :

```env
PAYDUNYA_SANDBOX=true
PAYDUNYA_MASTER_KEY=valeur-de-la-master-key
PAYDUNYA_PRIVATE_KEY=valeur-de-la-private-key-test
PAYDUNYA_TOKEN=valeur-du-token-test
PAYDUNYA_STORE_NAME=JambaarPay
```

Ces trois clés proviennent de la même application PayDunya en mode test.
Elles sont utilisées uniquement par le serveur dans les en-têtes
`PAYDUNYA-MASTER-KEY`, `PAYDUNYA-PRIVATE-KEY` et `PAYDUNYA-TOKEN`.
La clé publique n’est pas nécessaire à cette API HTTP/JSON. Ne pas ajouter de
clés PayDunya dans Angular. Utiliser des valeurs sans guillemets extérieurs.

Les variables `KKIAPAY_*` et `WAVE_CHECKOUT_*` ne sont plus utilisées.
`WAVE_PUBLIC_BASE_URL` n’est plus nécessaire : la page est hébergée par PayDunya.
Le JWT effectif doit rester celui de user-service ; les variables directes
`SECURITY_JWT_*` ont priorité sur les placeholders `JWT_*`.

L’URL de l’API est choisie automatiquement par `PAYDUNYA_SANDBOX` :

- `true` : `https://app.paydunya.com/sandbox-api/v1`
- `false` : `https://app.paydunya.com/api/v1`

Pour cette intégration, conserver **true**. Aucun changement en production
réelle n’est requis pour tester le parcours.

## Déploiement

Les secrets SSH/GHCR existants restent identiques : `VPS_HOST`, `VPS_USER`,
`VPS_SSH_KEY`, `VPS_KNOWN_HOSTS`, `GHCR_PULL_USERNAME`, `GHCR_PULL_TOKEN`.
Le namespace par défaut est `jambarpay` (`K8S_NAMESPACE`).

1. Renseigner le secret complet `WAVE_SERVICE_ENV` avec le bloc ci-dessus.
2. Déployer wave_service, puis wallet_service (migration additive Flyway V4),
   puis Webjambarpay. Éviter de lancer des paiements pendant cette transition.
3. Se reconnecter au compte entreprise et créer un nouveau chargement.
   Les anciens chargements KkiaPay ne sont pas convertis : vérifier tout
   éventuel paiement ancien avant de repartir sur une nouvelle demande.
4. Utiliser des comptes de démonstration. Le paiement fournisseur est simulé,
   mais sa confirmation applique les soldes dans la base de l’application.

Contrôler les variables du conteneur depuis le VPS sans afficher les clés :

```bash
kubectl -n jambarpay exec deployment/wave-service -- sh -c '
printf "PAYDUNYA_SANDBOX=%s\n" "${PAYDUNYA_SANDBOX:-ABSENTE}"
for key in PAYDUNYA_MASTER_KEY PAYDUNYA_PRIVATE_KEY PAYDUNYA_TOKEN; do
  value=$(printenv "$key" 2>/dev/null || true)
  if [ -n "$value" ]; then status=RENSEIGNEE; else status=ABSENTE_OU_VIDE; fi
  printf "%s=%s\n" "$key" "$status"
done
'
kubectl -n jambarpay get pods -l 'app in (wallet-service,wave-service)'
```

## Parcours et confirmation

Le serveur crée la facture avec `POST /checkout-invoice/create` : montant
entier XOF, nom de boutique, référence du chargement dans
`custom_data.payment_reference` et URL de retour comprenant `topUpId`.
La réponse doit avoir `response_code=00` et fournir un token et une URL
PayDunya du même environnement. Ces informations sont enregistrées avant
la redirection du navigateur.

PayDunya ajoute `token` à l’URL de retour. Angular le transmet à wallet-service,
qui vérifie la facture par `GET /checkout-invoice/confirm/{token}`.
Seul `status=completed` autorise le crédit, après contrôle du code `00`,
du hash SHA-512 de la Master Key, du mode `test`, du token, du montant et
de la référence du chargement. Un simple retour navigateur n’est pas une preuve
que le paiement a réussi. Les portefeuilles, le reçu et la confirmation sont
écrits dans une seule transaction SQL ; les confirmations répétées sont idempotentes.

Une facture en attente est réutilisée. Le bouton Reprendre vérifie d’abord son
état : si elle est déjà payée, le chargement est confirmé sans repayer ; seules
les factures annulées ou échouées peuvent être remplacées. L’indication locale
`expiresAt` de 24 heures ne remplace pas le statut renvoyé par PayDunya.

Cette version confirme au retour navigateur, à la reprise du paiement ou avec
une référence de facture connue. Elle ne configure pas encore d’IPN : fermer
l’onglet avant le retour impose de reprendre le chargement pour confirmer.
L’IPN documentée utilise un POST `application/x-www-form-urlencoded` ; aucun
callback non implémenté n’est envoyé à PayDunya.

## Diagnostic

### Pod bloqué sur une ancienne clé KkiaPay

Si les événements indiquent `CreateContainerConfigError` et
`couldn't find key KKIAPAY_SANDBOX`, le Deployment conserve une ancienne
référence explicite vers une clé retirée du secret. Le conteneur n’a pas encore
démarré ; augmenter le timeout du rollout ne corrige pas cette configuration.

Le workflow exécute désormais `reconcile-wave-service-env.py` après l’application
du manifeste. Ce nettoyage retire uniquement les anciennes entrées KkiaPay et
les paramètres de page de checkout supprimés. Il préserve les clés PayDunya,
JWT et Eureka ainsi que l’image ; il ne modifie aucun Secret.

Pour réparer le Deployment existant depuis un terminal **local** :

```bash
ssh ubuntu@149.202.61.30 'python3 - --namespace jambarpay --apply && kubectl -n jambarpay rollout status deployment/wave-service --timeout=600s' < /home/abdoulaye/Bureau/JambarPay/wave_service/.github/scripts/reconcile-wave-service-env.py
```

Le changement du template déclenche le remplacement des pods. Sans `--apply`,
le script affiche uniquement les noms des entrées à retirer. Les valeurs ne
sont jamais affichées. Un Deployment déjà nettoyé n’est pas modifié.

### Requêtes de paiement

- HTTP 400 de `/api/v1/wave/checkout-links` : lire `code` et `message` du JSON.
- HTTP 503 : consulter les lignes `PayDunya request failed:` dans wave-service
  et `Employee top-up checkout failed:` dans wallet-service. Elles ne contiennent
  ni clés ni corps de réponse fournisseur.
- Un refus HTTP 200 avec `response_code` différent de `00` reste un échec.
- Un statut `pending`, `cancelled` ou `failed` ne crédite aucun salarié.

```bash
kubectl -n jambarpay logs deployment/wave-service --since=15m | grep -F 'PayDunya request failed:'
kubectl -n jambarpay logs deployment/wallet-service --since=15m | grep -F 'Employee top-up checkout failed:'
```

Les tests utilisent des réponses HTTP simulées et une base PostgreSQL locale
jetable. Ils ne contactent pas PayDunya et ne déclenchent aucun paiement.
