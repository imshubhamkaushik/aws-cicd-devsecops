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

trap 'error "Backend bootstrap failed at line $LINENO"' ERR

# START
info "Starting Full Bootstrap Process..."

# APPLY BACKEND TERRAFORM
info "Running Terraform Backend Bootstrap..." 

cd "$ROOT_DIR/terraform/backend-bootstrap" 
terraform init 
terraform fmt -check 
terraform validate 
terraform plan -out=tfplan 
terraform apply -auto-approve tfplan

info "Backend Bootstrap Complete."

# TRIGGER NEXT PHASE - BOOTSTRAP-INFRA 
info "Starting Bootstrap Infrastructure Phase..."
bash "$ROOT_DIR/scripts/bootstrap-infra.sh"

info "Bootstrap Completed Successfully."
```
