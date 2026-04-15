```bash id="3"
#!/bin/bash

set -euo pipefail

# GLOBALS
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

cleanup() {
    rm -f "$ROOT_DIR/terraform/backend-bootstrap/tfplan" 2>/dev/null || true
}

trap 'error "Backend bootstrap failed at line $LINENO"' ERR
trap cleanup EXIT

# CHECK DEPENDENCIES
check_dependencies() {
    local deps=("terraform" "aws")
    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &>/dev/null; then
            error "Required dependency '$dep' is not installed or not on PATH."
            exit 1
        fi
    done
}

# START
info "Starting Full Bootstrap Process..."

check_dependencies

# APPLY BACKEND TERRAFORM
info "Running Terraform Backend Bootstrap..." 

cd "$ROOT_DIR/terraform/backend-bootstrap" 
terraform init 
terraform fmt -check 
terraform validate 
terraform plan -out main.tfplan 
terraform apply -auto-approve main.tfplan
# NOTE: -auto-approve is intentional here — the backend-bootstrap only creates one S3 bucket(S3 lock only with use_lockfile).
# It has no meaningful "review" step, unlike bootstrap-infra which provisions EC2.

info "Backend Bootstrap Complete."

# TRIGGER NEXT PHASE - BOOTSTRAP-INFRA 
info "Starting Bootstrap Infrastructure Phase..."
bash "$ROOT_DIR/scripts/bootstrap-infra.sh"

info "Bootstrap Completed Successfully."
```
