#!/usr/bin/env bash
# install-all.sh — one command to get a bare Linux machine ready.
#
#   1. install-python.sh   (shell)   makes sure Python 3 exists. The only job for shell.
#   2. install_tools.py    (Python)  installs AWS CLI, Terraform, Ansible, boto3, jq, ssh-keygen.
#
# Works on a laptop, an EC2 instance, or a VM on any other cloud, as long as it is Linux with
# apt, dnf, yum or zypper. On Windows, run it inside WSL (Ubuntu).
#
# Usage (from the repository root):
#   bash scripts/bash/install-all.sh
#   bash scripts/bash/install-all.sh --only terraform,aws     # extra arguments go to install_tools.py
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${DIR}/../.." && pwd)"

bash "${DIR}/install-python.sh"

echo -e "\n\033[1;34m==>\033[0m Installing the remaining tools with Python"
python3 "${REPO_ROOT}/scripts/python/install_tools.py" "$@"

echo ""
echo "Next steps:"
echo "    1. aws configure                          set up your AWS credentials"
echo "    2. bash scripts/bash/create-key-pair.sh   generate the EC2 SSH key pair"
echo "    3. python3 scripts/python/bootstrap.py    bootstrap the platform"
