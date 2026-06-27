#!/usr/bin/env bash
# install-all.sh — runs all four install-*.sh scripts in order. Each one is
# also independently runnable (e.g. `./install-terraform.sh` on its own if
# that's the only thing you need, or to re-run just one after an apt issue).
#
# Order matters once: install-ansible.sh installs Galaxy collections that
# assume Python (for boto3) is already there, so install-python.sh runs
# before it. AWS CLI and Terraform have no ordering dependency on anything
# else here, but run first since they're the quickest wins to confirm
# the script works at all.
#
# Usage:
#   chmod +x install-all.sh install-*.sh lib.sh
#   ./install-all.sh
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DIR}/../.." && pwd)"

"${DIR}/install-awscli.sh"
"${DIR}/install-terraform.sh"
"${DIR}/install-python.sh"
"${DIR}/install-ansible.sh" "${REPO_ROOT}"

echo -e "\n\033[1;34m==>\033[0m All done. Versions installed:"
echo "    $(aws --version)"
echo "    $(terraform version | head -n1)"
echo "    $(python3 --version)"
echo "    $(ansible --version | head -n1)"
echo ""
echo "Next steps:"
echo "    1. aws configure                 — set up your AWS credentials"
echo "    2. ./create-key-pair.sh          — generate the EC2 SSH key pair"
echo "    3. python3 scripts/python/bootstrap.py full"
