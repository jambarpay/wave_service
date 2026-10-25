# Wave Service Deployment

## GitHub secrets

- `VPS_HOST`
- `VPS_USER`
- `VPS_SSH_KEY`
- `VPS_KNOWN_HOSTS`
- `GHCR_PULL_USERNAME`
- `GHCR_PULL_TOKEN`
- `WAVE_SERVICE_ENV`

## GitHub variables

- `K8S_NAMESPACE=jambarpay`

## `WAVE_SERVICE_ENV`

```env
WAVE_SERVICE_NAME=wave-service
WAVE_SERVICE_PORT=8088
EUREKA_SERVER_URL=http://eureka-server:8761/eureka/
EUREKA_REGISTER=true
EUREKA_FETCH=true
WAVE_PUBLIC_BASE_URL=https://jambaarpay.com
WAVE_CHECKOUT_SIGNING_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
WAVE_CHECKOUT_DEFAULT_LOGO_URL=
WAVE_CHECKOUT_DEFAULT_THEME=#0095ff
WAVE_CHECKOUT_LINK_TTL_MINUTES=30
KKIAPAY_SANDBOX=true
KKIAPAY_BASE_URL=
KKIAPAY_PUBLIC_KEY=your-public-key
KKIAPAY_PRIVATE_KEY=your-private-key
KKIAPAY_SECRET_KEY=your-secret-key
JWT_ISSUER=jambaarpay-user-service
JWT_SECRET=replace-with-the-same-random-secret-used-by-user-service-and-api-gateway
```

## Manual diagnose

### KkiaPay environments and browser payment errors

An HTTP `400` from `https://api-sandbox.kkiapay.me/api/v1/payments/wave`
comes from the KkiaPay widget's payment request. This URL confirms that the
widget is using sandbox; the HTTP status alone does not identify the refusal.
Inspect that request's **Response** in the browser network panel and retain
only the provider error code and message, without API keys or phone numbers.

KkiaPay's [sandbox test guide](https://docs.kkiapay.me/v1/compte/kkiapay-sandbox-guide-de-test)
requires sandbox API keys and the documented test data for simulations.
Its [JavaScript SDK documentation](https://docs.kkiapay.me/v1/plugin-et-sdk/sdk-javascript)
describes the `sandbox` flag. A production payment requires an activated
merchant account and its production key set; changing the flag alone does not
convert sandbox keys into production keys.

The reported attempt used a real Senegal Wave number with sandbox enabled.
For a documented successful simulation, select **MTN Benin** in the widget
and use **61000000**. KkiaPay also documents **61000001** for a processing-error
scenario. Use a test amount such as **1000 XOF**; this is an example, not a
documented minimum. The consulted test guide does not list a Senegal Wave
test number: obtain that scenario from KkiaPay to verify Wave specifically.
The checkout page now displays these sandbox instructions before opening
the widget and waits for a click on the payment button so they can be read.
Production checkouts keep automatic opening and do not display test-number
instructions.

For the employee balance-loading flow, a successful simulation must still be
verified server-side by `wave-service`, then credited to the company's wallet
by `wallet-service`, and distributed through the bulk-transfer endpoint.
A refused widget payment has not reached these crediting steps.

| Environment | `KKIAPAY_SANDBOX` | Verification API with empty `KKIAPAY_BASE_URL` |
| --- | --- | --- |
| Sandbox | `true` | `https://api-sandbox.kkiapay.me` |
| Production | `false` | `https://api.kkiapay.me` |

Keep `KKIAPAY_BASE_URL` empty unless an explicit override is needed. The service
already selects the matching verification API from `KKIAPAY_SANDBOX`. An
explicit sandbox URL overrides that selection even when the flag is `false`,
which can prevent verification after a production payment. This setting
controls server verification; it does not override the widget's sandbox flag.

Inspect the deployed settings without exposing keys:

```bash
kubectl -n jambarpay exec deployment/wave-service -- sh -c '
printf "sandbox=%s\nverification_api=%s\n" "$KKIAPAY_SANDBOX" "${KKIAPAY_BASE_URL:-automatique}"
if [ -n "$KKIAPAY_PUBLIC_KEY" ]; then printf "public_key=RENSEIGNÉ\n"; else printf "public_key=VIDE\n"; fi
if [ -n "$KKIAPAY_PRIVATE_KEY" ]; then printf "private_key=RENSEIGNÉ\n"; else printf "private_key=VIDE\n"; fi
if [ -n "$KKIAPAY_SECRET_KEY" ]; then printf "secret_key=RENSEIGNÉ\n"; else printf "secret_key=VIDE\n"; fi
'
```

Do not infer that Wave sandbox is unsupported, or that a payment succeeded,
from HTTP `400` alone. No provider request or real payment is needed to inspect
these settings. Update the complete `WAVE_SERVICE_ENV` secret when correcting
the environment, preserving its other service settings, then redeploy and
generate a fresh checkout link.

### Checkout creation and deployment

The web checkout returns to `/enterprise-employees/charge-balances` on the
origin that initiated the payment, so its pending recharge remains available in
the browser session. `WAVE_PUBLIC_BASE_URL` must point to a public gateway URL
serving `/api/v1/wave/checkout/{token}`. This signed, expiring page is accessible
without a bearer token; checkout creation and transaction verification require
authentication.

For a `400` from `POST /api/v1/wave/checkout-links`, inspect the JSON `message`
field. Link creation requires `KKIAPAY_PUBLIC_KEY` and a signing secret of at
least 32 bytes (`WAVE_CHECKOUT_SIGNING_SECRET`, or `KKIAPAY_SECRET_KEY` as a
fallback). Configure these in `WAVE_SERVICE_ENV` and redeploy if they are missing
or too short.

If the page displays `KKIAPAY public key is not configured`, set the real
`KKIAPAY_PUBLIC_KEY` in the `WAVE_SERVICE_ENV` GitHub Actions secret of the
Wave service repository. Include the corresponding `KKIAPAY_PRIVATE_KEY` and
`KKIAPAY_SECRET_KEY` for transaction verification, and keep `KKIAPAY_SANDBOX`
consistent with those keys. The `.env.example` file is a template; editing it
does not update the deployed service.

After updating the secret, rerun `Deploy On Main`. The deployment bundle hashes
the environment file into the pod template, so changes to credentials restart
the pods even when the image version stays the same.

```bash
sudo kubectl -n jambarpay get deployment wave-service
sudo kubectl -n jambarpay get svc wave-service
sudo kubectl -n jambarpay get pods -l app=wave-service -o wide
sudo kubectl -n jambarpay logs deployment/wave-service --tail=200
sudo kubectl -n jambarpay get events --sort-by=.metadata.creationTimestamp | tail -n 50
```
