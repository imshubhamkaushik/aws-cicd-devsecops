#!/usr/bin/env bash
# install-awscli.sh — installs AWS CLI v2 from the official AWS installer.
# Not via apt — Ubuntu's repos carry CLI v1 or stale v2 builds.
# Safe to re-run: --update refreshes an existing install in place.
#
# Usage: ./install-awscli.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_apt
install_base_packages

log "AWS CLI v2"
if command -v aws >/dev/null 2>&1 && aws --version 2>&1 | grep -q "aws-cli/2"; then
    skip "$(aws --version 2>&1)"
else
    TMP_DIR="$(mktemp -d)"
    ARCH="$(uname -m)"   # x86_64 or aarch64 — matches AWS's published artifact names
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${ARCH}.zip" -o "${TMP_DIR}/awscliv2.zip"
    unzip -q "${TMP_DIR}/awscliv2.zip" -d "${TMP_DIR}"
    sudo "${TMP_DIR}/aws/install" --update
    rm -rf "${TMP_DIR}"
    ok "$(aws --version)"
fi

echo ""
echo "Next: aws configure"
