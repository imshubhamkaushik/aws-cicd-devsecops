#!/usr/bin/env bash
# install-terraform.sh — installs Terraform from HashiCorp's official apt
# repo, so `apt upgrade` keeps it current. providers.tf in this project
# requires >= 1.12.0 — the repo's current stable release satisfies that.
#
# Usage: ./install-terraform.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_apt
install_base_packages

log "Terraform"
if command -v terraform >/dev/null 2>&1; then
    skip "$(terraform version | head -n1)"
else
    wget -qO- https://apt.releases.hashicorp.com/gpg | \
        gpg --dearmor | sudo tee /usr/share/keyrings/hashicorp-archive-keyring.gpg >/dev/null
    echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | \
        sudo tee /etc/apt/sources.list.d/hashicorp.list >/dev/null
    sudo apt-get update -qq
    sudo apt-get install -y -qq terraform >/dev/null
    ok "$(terraform version | head -n1)"
fi
