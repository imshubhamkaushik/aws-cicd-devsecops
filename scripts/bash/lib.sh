#!/usr/bin/env bash
#
# lib.sh — shared helpers for install-*.sh. Not meant to be run directly;
# each install-*.sh does `source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"`.
#
set -euo pipefail

log()  { echo -e "\n\033[1;34m==>\033[0m $*"; }
ok()   { echo -e "    \033[1;32m✓\033[0m $*"; }
skip() { echo -e "    \033[1;33m·\033[0m $* (already installed, skipping)"; }

require_apt() {
    if [[ "$(id -u)" -eq 0 ]]; then
        echo "Run this as your normal user, not root/sudo. It calls sudo itself" >&2
        echo "only for the specific commands that need it." >&2
        exit 1
    fi
    if ! command -v apt-get >/dev/null 2>&1; then
        echo "This script only supports apt-based distros (Ubuntu/Debian)." >&2
        exit 1
    fi
}

# Common base packages several of the install-*.sh scripts assume are
# present (curl, unzip, gnupg for adding apt repos, etc). Safe to call from
# more than one script in the same run — apt-get is idempotent.
install_base_packages() {
    log "Base packages (curl, unzip, gnupg, etc.)"
    sudo apt-get update -qq
    sudo apt-get install -y -qq \
        curl wget unzip gnupg ca-certificates lsb-release \
        software-properties-common apt-transport-https \
        git jq >/dev/null
    ok "Base packages present"
}
