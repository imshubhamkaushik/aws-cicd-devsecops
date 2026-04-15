#!/bin/bash

set -euo pipefail

# CHECK DIRECTORY
check_directory() {
    if [[ ! -d "$1" ]]; then
        echo "ERROR: Directory '$1' not found."
        exit 1
    fi
}

# CHECK DEPENDENCIES
check_dependencies() {
    local deps=("terraform" "aws" "ansible-playbook" "jq")

    for dep in "${deps[@]}"; do
        if ! command -v "$dep" &>/dev/null; then
            echo "ERROR: Required dependency '$dep' not installed."
            exit 1
        fi
    done
}

# CHECK AWS AUTHENTICATION
check_aws_auth() {
    if ! aws sts get-caller-identity &>/dev/null; then
        echo "ERROR: AWS credentials not configured."
        exit 1
    fi
}

# GLOBALS
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

PROJECT_TAG="catalogix-bootstrap"

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

trap 'error "Bootstrap infra failed at line $LINENO"' ERR

# PRE-FLIGHT CHECKS
check_dependencies
check_aws_auth

# APPLY BOOTSTRAP INFRA
info "Running Bootstrap Infrastructure Terraform..."

cd "$ROOT_DIR/terraform/bootstrap-infra"

terraform init
terraform fmt -check
terraform validate
terraform plan -out main.tfplan

echo ""
terraform show main.tfplan
echo ""
read -rp "Review the plan above. Proceed with Terraform apply? (yes/no): " confirm
if [[ "$confirm" != "yes || y" ]]; then
    info "Aborted by user. No infrastructure was changed."
    exit 0
fi

terraform apply main.tfplan
rm-f main.tfplan

info "Bootstrap Infrastructure Complete."

# WAIT FOR EC2 HEALTH
info "Waiting for Bootstrap EC2 Instances to pass status checks..."

INSTANCE_IDS=$(aws ec2 describe-instances \
    --filters "Name=tag:Project,Values=${PROJECT_TAG}" \
    --query "Reservations[*].Instances[*].InstanceId" \
    --output text)

if [[ -z "$INSTANCE_IDS" ]]; then
    error "No EC2 instances found with tag Project=${PROJECT_TAG}. Did Terraform apply succeed?"
    exit 1
fi

info "Found instances: ${INSTANCE_IDS}"
aws ec2 wait instance-status-ok --instance-ids $INSTANCE_IDS

info "EC2 Instances Healthy."

# RUN ANSIBLE CONFIGURATION
info "Running Ansible Configuration..."

cd "$ROOT_DIR/ansible"

ansible-galaxy collection install -r requirements.yaml
ansible-playbook playbook.yaml

info "Bootstrap Infra Completed Successfully."
