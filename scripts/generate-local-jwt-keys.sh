#!/usr/bin/env bash
# Generates the two RSA key pairs the application needs and writes them into .env.
#
#   1. User access-token pair: the PRIVATE key goes only to user-svc (it signs login tokens);
#      every service gets the PUBLIC key (to check them).
#   2. Internal pair: one key pair shared by all services, used when one service calls another
#      with no user involved (checkout-svc -> payment-svc, and so on).
#
# Re-running replaces both pairs, so every token already issued stops working.
#
# Usage: scripts/generate-local-jwt-keys.sh [--kubernetes [namespace]]
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
K8S_NAMESPACE=""
if [[ "${1:-}" == "--kubernetes" ]]; then
  K8S_NAMESPACE="${2:-catalogix}"
  shift $(( $# >= 2 ? 2 : 1 ))
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--kubernetes [namespace]]" >&2
  exit 2
fi

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || cp "$ROOT/.env.example" "$ENV_FILE"

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

base64_no_wrap() { base64 | tr -d '\n'; }

# new_keypair <name> <bits>  ->  sets <NAME>_PRIVATE_B64 and <NAME>_PUBLIC_B64 (Base64 of DER)
new_keypair() {
  local name="$1" bits="$2"
  openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:${bits}" -out "$TEMP_DIR/$name-private.pem" >/dev/null 2>&1
  openssl pkey -in "$TEMP_DIR/$name-private.pem" -pubout -out "$TEMP_DIR/$name-public.pem" >/dev/null 2>&1
  printf -v "${name^^}_PRIVATE_B64" '%s' "$(openssl pkcs8 -topk8 -nocrypt -in "$TEMP_DIR/$name-private.pem" -outform DER | base64_no_wrap)"
  printf -v "${name^^}_PUBLIC_B64" '%s' "$(openssl pkey -pubin -in "$TEMP_DIR/$name-public.pem" -outform DER | base64_no_wrap)"
}

new_keypair user 3072
new_keypair internal 2048

# Stage the values in a mode-600 file so they never appear in process arguments.
UPDATES_FILE="$TEMP_DIR/new-jwt-keys.env"
printf '%s=%s\n' \
  JWT_PRIVATE_KEY_B64 "$USER_PRIVATE_B64" \
  JWT_PUBLIC_KEY_B64 "$USER_PUBLIC_B64" \
  INTERNAL_JWT_PRIVATE_KEY_B64 "$INTERNAL_PRIVATE_B64" \
  INTERNAL_JWT_PUBLIC_KEY_B64 "$INTERNAL_PUBLIC_B64" > "$UPDATES_FILE"
chmod 600 "$UPDATES_FILE"

python3 - "$ENV_FILE" "$UPDATES_FILE" <<'PY'
import os, pathlib, sys
path = pathlib.Path(sys.argv[1])
updates = {}
for line in pathlib.Path(sys.argv[2]).read_text().splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        updates[key] = value
lines = path.read_text().splitlines() if path.exists() else []
result, written = [], set()
for line in lines:
    key = line.split("=", 1)[0].strip() if "=" in line else ""
    # Drop leftovers from older versions (shared secret, per-service keys).
    if key in {"JWT_SECRET", "JWT_SECRET_PREVIOUS"} or key.startswith("SERVICE_JWT_"):
        continue
    if key in updates:
        if key not in written:
            result.append(f"{key}={updates[key]}")
            written.add(key)
        continue
    result.append(line)
for key, value in updates.items():
    if key not in written:
        result.append(f"{key}={value}")
path.write_text("\n".join(result).rstrip() + "\n")
os.chmod(path, 0o600)
PY

echo "Generated the user and internal RS256 key pairs and updated $ENV_FILE (permissions 600)."
echo "Only user-svc receives JWT_PRIVATE_KEY_B64; every service receives the internal key pair."

if [[ -n "$K8S_NAMESPACE" ]]; then
  command -v kubectl >/dev/null || { echo "kubectl is required for --kubernetes" >&2; exit 1; }
  K8S_ENV_FILE="$TEMP_DIR/jwt-keys-k8s.env"
  printf '%s=%s\n' \
    jwt_private_key_b64 "$USER_PRIVATE_B64" \
    jwt_public_key_b64 "$USER_PUBLIC_B64" \
    internal_jwt_private_key_b64 "$INTERNAL_PRIVATE_B64" \
    internal_jwt_public_key_b64 "$INTERNAL_PUBLIC_B64" > "$K8S_ENV_FILE"
  chmod 600 "$K8S_ENV_FILE"
  kubectl create namespace "$K8S_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
  kubectl create secret generic catalogix-jwt-keys --namespace "$K8S_NAMESPACE" \
    --from-env-file="$K8S_ENV_FILE" --dry-run=client -o yaml | kubectl apply -f -
  echo "Created/updated Kubernetes Secret catalogix-jwt-keys in namespace $K8S_NAMESPACE."
fi
