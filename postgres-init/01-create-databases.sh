#!/bin/bash
# Runs once, on first start of an empty data volume.
# Mirrors terraform/platform-infra/modules/db-roles: one database and one
# login role per service, the role owning only its own database.
set -euo pipefail

: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD must be set}"

# role:database
SERVICES="
user_svc:catalogix-users
catalog_svc:catalogix-catalog
inventory_svc:catalogix-inventory
cart_svc:catalogix-cart
payment_svc:catalogix-payment
checkout_svc:catalogix-checkout
notification_svc:catalogix-notification
"

for entry in $SERVICES; do
  role="${entry%%:*}"
  db="${entry##*:}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v role="$role" -v db="$db" -v pw="$SERVICE_DB_PASSWORD" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'role')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec
EOSQL
done
