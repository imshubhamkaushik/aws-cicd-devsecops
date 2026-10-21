# Authentication in Catalogix, in one page

There are only **two kinds of tokens** and **two key pairs**. Everything else is plumbing that carries those keys to the right place.

## The two tokens

| Token | Who creates it | Who checks it | Lives | Used for |
|---|---|---|---|---|
| **User access token** (JWT) | `user-svc` at login | every service | 15 minutes | "This request comes from user 42, role USER" |
| **Service token** (JWT) | the calling service itself | the receiving service | 2 minutes | "checkout-svc is calling payment-svc on its own" (no user involved, e.g. background retries) |

The user's **refresh token** is not a JWT. It is a random string stored hashed in the database and sent as an HttpOnly cookie. It only gets a new access token when the old one expires.

## The two key pairs (four variables)

| Key pair | Private key (signs) | Public key (verifies) | Who holds the private key |
|---|---|---|---|
| **User keys** | `JWT_PRIVATE_KEY_B64` | `JWT_PUBLIC_KEY_B64` | **only `user-svc`** |
| **Internal keys** | `INTERNAL_JWT_PRIVATE_KEY_B64` | `INTERNAL_JWT_PUBLIC_KEY_B64` | every service (shared, a documented trade-off) |

Rule of thumb: *private key = can create tokens, public key = can only check them.* That is why six services never need the user private key: they can verify a login but cannot forge one.

## Where the four variables come from

| Environment | Created by | Delivered by |
|---|---|---|
| Docker Compose | `python3 scripts/python/local/generate_jwt_keys.py` writes them into `.env` | `docker-compose.yaml` (defined once in `x-backend-env`, merged into every backend) |
| AWS | Terraform generates the keys | AWS Secrets Manager -> External Secrets Operator -> Kubernetes Secret `catalogix-jwt-keys` -> env vars (one helper in `infra/helm/catalogix-hc/templates/_helpers.tpl`) |

You never type or copy these values by hand in either environment.

## What happens on a request

1. Browser logs in -> `user-svc` signs an access token with the **user private key**.
2. Browser calls `/api/orders` with `Authorization: Bearer <token>` -> gateway forwards it.
3. `checkout-svc`'s `JwtAuthFilter` checks the signature with the **user public key**, then reads the user id and role.
4. `checkout-svc` needs to call `payment-svc` -> it signs a short service token with the **internal private key**, addressed to `payment-svc`.
5. `payment-svc` checks it with the **internal public key** and its own name as the audience.

All of this code is in `app/backend/security-common` (`JwtService`, `JwtAuthFilter`): about 500 lines shared by all services.

## Why not Spring Security?

Spring Security would give the same two checks (verify signature, read claims) in different code. It would not remove a single one of the four variables, because verifying a token always needs a public key. It would add a security filter chain to seven services and rework about 14 test classes, so it was not adopted. See the README design decisions.
