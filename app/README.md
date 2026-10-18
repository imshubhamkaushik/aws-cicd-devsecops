# app/

Everything that runs as part of Catalogix. The infrastructure that deploys it is in [`../infra`](../infra/README.md).

```text
app/
  backend/         security-common and seven Spring Boot services (Maven modules, parent pom.xml at the repository root)
  frontend/        React 19 + Vite 8 + Tailwind CSS 4 single-page app, built to static files and served by Nginx
  gateway/         Nginx reverse proxy: routing, rate limiting, security headers
  postgres-init/   01-create-databases.sh: one database and one login role per service (Docker Compose only)
```

For a guided tour of how a request and an order travel through the services, read [../docs/app-guide.md](../docs/app-guide.md). This page is the reference.

## Services

| Service | Port | Database (role) | RabbitMQ | Notes |
|---|---|---|---|---|
| `user-svc` | 11005 | `catalogix-users` (`user_svc`) | publishes | Accounts, login, sessions, addresses, roles |
| `catalog-svc` | 11002 | `catalogix-catalog` (`catalog_svc`) | no | Products and search; seeds sample data under a PostgreSQL advisory lock |
| `inventory-svc` | 11003 | `catalogix-inventory` (`inventory_svc`) | no | Stock changes only here; internal only |
| `cart-svc` | 11004 | `catalogix-cart` (`cart_svc`) | no | Per-user cart and the checkout handoff |
| `payment-svc` | 11006 | `catalogix-payment` (`payment_svc`) | no | Mock payments; internal only |
| `checkout-svc` | 11007 | `catalogix-checkout` (`checkout_svc`) | publishes | Orders, saga, outboxes |
| `notification-svc` | 11008 | `catalogix-notification` (`notification_svc`) | consumes | Email and the notification log |
| `gateway` | 11000 | none | no | The only published port; `/gateway/health` is its health check |
| `frontend` | 11001 | none | no | Static files |

Database names contain hyphens, so they must be quoted in SQL. The init script does this with `%I`. The same names are created on Docker Compose (`postgres-init`), on local Kubernetes (`infra/helm/catalogix-hc/templates/postgres-local.yaml`) and on RDS (`infra/terraform/platform-infra/modules/db-roles`). The three must stay in step.

Every service validates its schema against Flyway migrations (`src/main/resources/db/migration/*.sql`) with `spring.jpa.hibernate.ddl-auto=validate`; Hibernate never changes the schema.

### Gateway routes

| Path | Goes to |
|---|---|
| `/api/users/login`, `/api/users/register`, `/api/users/refresh` | user-svc, stricter rate limit |
| `/api/users/...` | user-svc |
| `/api/products/...` | catalog-svc |
| `/api/orders/...`, `/api/admin/...` | checkout-svc |
| `/api/cart/...` | cart-svc |
| `/api/notifications/...` | notification-svc |
| everything else | frontend |

`/api` is stripped before the request reaches the service. The gateway limits clients to 20 requests per second (burst 40) in general and 5 per second (burst 10) on login, register and refresh. It overwrites `X-Real-IP`, and the services trust that header only because of that.

## Order flow in one paragraph

`checkout-svc` runs the saga without a distributed transaction. It reads prices from `catalog-svc`, reserves stock in `inventory-svc` (with operation ids derived from the idempotency key), fetches the shipping address from `user-svc` and saves the order as `PENDING_PAYMENT`. Paying first moves the order to `PAYMENT_PROCESSING` so a second pay or cancel is rejected, then calls `payment-svc` outside any database transaction. Confirmed, declined and unknown results are handled explicitly. Stock releases that fail are written to `compensation_outbox` and retried; confirmation and cancellation events are written to `order_event_outbox` in the same transaction as the order change and relayed to RabbitMQ. Full detail: [../docs/app-guide.md](../docs/app-guide.md).

### Outboxes

| Table | Written when | Retried by | Gives up after |
|---|---|---|---|
| `compensation_outbox` | A stock release could not be sent | `CompensationOutboxProcessor` | 5 attempts, then `DEAD_LETTER` |
| `order_event_outbox` | An order is confirmed or cancelled | `OrderEventOutboxProcessor` | 8 attempts, then `DEAD_LETTER` |

Both claim up to 10 rows at a time with `FOR UPDATE SKIP LOCKED`, so several replicas can work safely. Dead letters from the compensation outbox can be listed and retried by an admin (`GET /admin/outbox`, `POST /admin/outbox/{id}/retry`, and the `/admin/outbox` page in the UI). Gauges `checkout_outbox_pending_count`, `checkout_outbox_dead_letter_count`, `checkout_order_event_outbox_pending_count` and `checkout_order_event_outbox_dead_letter_count` feed the alerts.

A pending-order job in `checkout-svc` cancels orders still in `PENDING_PAYMENT` after `ORDER_PAYMENT_TIMEOUT_MINUTES` (default 30) and returns orders stuck in `PAYMENT_PROCESSING` for longer than `PAYMENT_CLAIM_TIMEOUT_MINUTES` (default 5) to `PENDING_PAYMENT`.

Delivery to RabbitMQ is at least once, and the email consumer does not store a receipt per message id, so a crash at the wrong moment can send one email twice.

## Authentication and authorisation

- **User tokens.** Short-lived RS256 JWTs. Only `user-svc` holds the private key (`JWT_PRIVATE_KEY_B64`); every service verifies with `JWT_PUBLIC_KEY_B64`.
- **Refresh tokens.** Opaque, stored only as a SHA-256 hash, delivered in an `httpOnly` cookie and rotated on every use.
- **Login lockout.** Five consecutive failures lock the account for 15 minutes.
- **Password reset and email verification.** One-time tokens stored as hashes. Reset links last one hour, verification links 24 hours. A reset revokes existing sessions.
- **Roles.** `USER`, `SELLER`, `ADMIN`. Registration only creates `USER`. A user can request seller access, and only an admin can grant `SELLER` or `ADMIN`. The last admin cannot be demoted.
- **Service-to-service tokens.** When one service calls another with no user, it signs a two-minute RS256 token with the shared internal key (`INTERNAL_JWT_PRIVATE_KEY_B64`). The token names the caller in `iss`, the target in `aud` and has the role `SYSTEM`. The receiver checks the signature, the audience, and, on internal endpoints, that the caller is the expected service:

  | Endpoint | Accepts `SYSTEM` tokens from |
  |---|---|
  | payment-svc payment and refund | `checkout-svc` only |
  | inventory-svc stock adjustment | `catalog-svc` only |
  | inventory-svc other internal operations | `checkout-svc` or `catalog-svc` |
  | user-svc notification preferences | `notification-svc` only |

  Because the key is shared, `iss` is a sanity check and not proof of identity; see the limitations in the root README.
- **Health endpoints.** `/health` and `/actuator/...` need no token on every backend service. Everything else does.
- **Rate limiting.** Each service also limits per client IP: `RATE_LIMIT_GENERAL_RPM` (default 300) and `RATE_LIMIT_AUTH_RPM` (default 10) requests per minute, switched by `RATE_LIMIT_ENABLED`. Health, actuator and CORS preflight requests are exempt. The gateway remains the primary limiter.

## Configuration

Configuration comes from environment variables. The full local list is in [`.env.example`](../.env.example) and in each service's block in [`docker-compose.yaml`](../docker-compose.yaml). On Kubernetes the same names are set by the Helm chart, with secrets coming from the `catalogix-secrets` and `catalogix-jwt-keys` Secrets.

| Variable | Used by | Meaning |
|---|---|---|
| `JWT_PRIVATE_KEY_B64` | user-svc | Private key that signs user access tokens |
| `JWT_PUBLIC_KEY_B64` | all services | Public key that verifies them |
| `INTERNAL_JWT_PRIVATE_KEY_B64`, `INTERNAL_JWT_PUBLIC_KEY_B64` | all services | The shared internal pair |
| `JWT_EXPIRATION_MS`, `REFRESH_TOKEN_EXPIRATION_MS` | user-svc | Token lifetimes (15 minutes and 7 days by default in `.env.example`) |
| `ALLOWED_ORIGINS` | all services | CORS origins. Empty disables CORS. Needed only when calling a service without the gateway |
| `MAIL_FROM`, `FRONTEND_BASE_URL` | notification-svc, user-svc | Sender address and the base URL used in email links |
| `RATE_LIMIT_*` | all services | See above |

Keys are Base64 of DER: PKCS#8 for private keys, X.509 for public keys. `python3 scripts/python/local/generate_jwt_keys.py` writes them into `.env`; on AWS Terraform generates them.

## Build and test

All commands run from the repository root.

```bash
mvn verify                       # every backend module; integration tests start PostgreSQL with Testcontainers
mvn -pl app/backend/checkout-svc -am verify   # one service and what it depends on
npm ci                           # frontend dependencies (needs Node 24 or newer)
npm test                         # frontend tests
npm run build                    # production bundle in app/frontend/build
npm start                        # Vite dev server on http://localhost:5173; it proxies /api to the gateway on localhost:11000, so start the Compose stack first
docker compose build             # all images
```

Java 21 and Maven are required for the backend. Docker is required for the integration tests and the images.

### Images

Backend Dockerfiles build from the repository root (the build context must include the root `pom.xml` and `security-common`):

```bash
docker build -f app/backend/user-svc/Dockerfile .
```

They use `maven:3.9.16-eclipse-temurin-21-alpine` to build and `gcr.io/distroless/java21-debian13:nonroot` to run, with a BuildKit cache mount for the Maven repository. The frontend and gateway images are `nginx:stable-alpine3.24`, run as UID 1000 and have a health check. The frontend image serves the static bundle on 11001; the gateway listens on 11000 and exposes Nginx `stub_status` on 8081 for the Prometheus exporter.

## Frontend

Pages live in `frontend/src/components/`, one component per page, with routes in `frontend/src/App.jsx`:

| Route | Who |
|---|---|
| `/login`, `/forgot-password`, `/reset-password`, `/verify-email` | Anyone, no token |
| `/`, `/products`, `/cart`, `/orders`, `/account` | Any signed-in user |
| `/my-products` | `SELLER` and `ADMIN` |
| `/users`, `/admin/moderation`, `/admin/outbox`, `/notifications` | `ADMIN` |

`frontend/src/api.jsx` attaches the access token to requests and refreshes it through the cookie when it expires. Styling is Tailwind CSS 4 with a few Radix components (dialog and dropdown menu) in `frontend/src/components/ui/`. Colours and fonts are CSS variables at the top of `frontend/src/index.css`, and the dark theme follows the browser setting. The build output is plain static files, so the Dockerfile, Helm chart and gateway do not depend on any framework server.
