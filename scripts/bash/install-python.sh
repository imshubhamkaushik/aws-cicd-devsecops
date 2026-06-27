#!/usr/bin/env bash
# install-python.sh — installs Python 3 + the two packages the
# amazon.aws.aws_ec2 Ansible inventory plugin needs under the hood
# (it shells out to boto3, even though nothing in Ansible's own docs makes
# that obvious from the inventory plugin name).
#
# Usage: ./install-python.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_apt
install_base_packages

log "Python 3"
sudo apt-get install -y -qq python3 python3-pip python3-venv >/dev/null
ok "$(python3 --version)"

log "Python packages (boto3, botocore)"
if python3 -c "import boto3, botocore" >/dev/null 2>&1; then
    skip "boto3/botocore"
else
    # --break-system-packages is required on Ubuntu 23.04+/Debian 12+ (PEP 668
    # marks system Python as "externally managed"). Safe here: we're installing
    # exactly two well-known packages system-wide for a tool (Ansible) that
    # itself runs from the system Python in this setup.
    if ! pip3 install --quiet boto3 botocore 2>/dev/null; then
        pip3 install --quiet --break-system-packages boto3 botocore
    fi
    ok "boto3/botocore installed"
fi
