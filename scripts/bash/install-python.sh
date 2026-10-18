#!/usr/bin/env bash
# install-python.sh — makes sure Python 3 (with pip and venv) exists.
#
# This is the ONLY thing done in shell. A bare Linux VM always has a shell but not always Python;
# once Python is there, scripts/python/install_tools.py installs everything else.
#
# Works on apt (Ubuntu/Debian), dnf/yum (Amazon Linux, RHEL, Fedora and clones) and zypper (SUSE).
# Run as a normal user (uses sudo) or as root (typical on a fresh cloud VM).
# Safe to re-run.
#
# Usage: ./scripts/bash/install-python.sh
set -euo pipefail

MIN_MINOR=9   # Python 3.9 or newer

log() { echo -e "\n\033[1;34m==>\033[0m $*"; }
ok()  { echo -e "    \033[1;32m✓\033[0m $*"; }

python_is_ready() {
    command -v python3 >/dev/null 2>&1 || return 1
    python3 -c "import sys; sys.exit(0 if sys.version_info >= (3, ${MIN_MINOR}) else 1)" || return 1
    python3 -c "import venv, ensurepip" >/dev/null 2>&1 || return 1
}

as_root() {
    if [[ "$(id -u)" -eq 0 ]]; then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        echo "Need root to install packages. Re-run as root or install sudo." >&2
        exit 1
    fi
}

log "Python 3"
if python_is_ready; then
    ok "$(python3 --version) is already installed, nothing to do"
    exit 0
fi

if command -v apt-get >/dev/null 2>&1; then
    as_root apt-get update -qq
    as_root apt-get install -y -qq python3 python3-pip python3-venv
elif command -v dnf >/dev/null 2>&1; then
    as_root dnf install -y -q python3 python3-pip
elif command -v yum >/dev/null 2>&1; then
    as_root yum install -y -q python3 python3-pip
elif command -v zypper >/dev/null 2>&1; then
    as_root zypper --non-interactive install python3 python3-pip
else
    echo "No supported package manager (apt-get, dnf, yum, zypper). Install Python 3.${MIN_MINOR}+ yourself, then run:" >&2
    echo "  python3 scripts/python/install_tools.py" >&2
    exit 1
fi

if ! python_is_ready; then
    echo "Python was installed but is older than 3.${MIN_MINOR} or is missing venv: $(python3 --version 2>&1)" >&2
    echo "Install a newer Python (for example python3.11 from your distro) and re-run." >&2
    exit 1
fi
ok "$(python3 --version)"
