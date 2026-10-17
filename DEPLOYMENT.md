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
KKIAPAY_BASE_URL=https://api-sandbox.kkiapay.me
KKIAPAY_PUBLIC_KEY=your-public-key
KKIAPAY_PRIVATE_KEY=your-private-key
KKIAPAY_SECRET_KEY=your-secret-key
JWT_ISSUER=jambaarpay-user-service
JWT_SECRET=replace-with-the-same-random-secret-used-by-user-service-and-api-gateway
```

## Manual diagnose

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

```bash
sudo kubectl -n jambarpay get deployment wave-service
sudo kubectl -n jambarpay get svc wave-service
sudo kubectl -n jambarpay get pods -l app=wave-service -o wide
sudo kubectl -n jambarpay logs deployment/wave-service --tail=200
sudo kubectl -n jambarpay get events --sort-by=.metadata.creationTimestamp | tail -n 50
```
