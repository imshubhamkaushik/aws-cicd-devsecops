#!/usr/bin/env bash
# install-ansible.sh — installs Ansible from the official Ansible PPA (the
# method ansible.com itself recommends for Ubuntu — Ubuntu's default
# "universe" ansible package is often several versions behind), then
# installs this project's required Galaxy collections.
#
# Depends on Python already being present — run install-python.sh first if
# you're running these individually rather than through install-all.sh.
#
# Usage: ./install-ansible.sh [path-to-repo-root]
#   path-to-repo-root defaults to two directories up from this script
#   (scripts/install/install-ansible.sh -> repo root), matching where this
#   bundle's install-all.sh would invoke it from.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_apt
install_base_packages

REPO_ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

log "Ansible"
if command -v ansible-playbook >/dev/null 2>&1; then
    skip "$(ansible --version | head -n1)"
else
    sudo apt-add-repository --yes --update ppa:ansible/ansible
    sudo apt-get install -y -qq ansible >/dev/null
    ok "$(ansible --version | head -n1)"
fi

# community.docker  -> sonarqube role's docker_container module
# amazon.aws        -> aws_ec2.yaml dynamic inventory plugin
REQS_FILE="${REPO_ROOT}/ansible/requirements.yaml"
log "Ansible Galaxy collections"
if [[ -f "${REQS_FILE}" ]]; then
    ansible-galaxy collection install -r "${REQS_FILE}"
    ok "Collections installed from ${REQS_FILE}"
else
    echo "    Couldn't find ${REQS_FILE}."
    echo "    Run manually once you're in the repo root:"
    echo "      ansible-galaxy collection install -r ansible/requirements.yaml"
fi
