"""Run the project's Python scripts inside the project virtualenv (.venv at the repo root).

install_tools.py creates that virtualenv and puts Ansible and boto3 in it. Instead of asking you
to "activate" it every time, each entry-point script calls reexec_in_venv() first: if .venv
exists and we are not already running from it, the script starts again with the venv's Python
and the venv's bin/ folder at the front of PATH (so `ansible-playbook` is found). If .venv does
not exist yet, nothing happens and the script carries on with the system Python.

Set CATALOGIX_NO_VENV=1 to skip this (the unit tests and CI do not need it).
"""

import os
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
VENV_DIR = REPO_ROOT / ".venv"
VENV_BIN = VENV_DIR / "bin"
VENV_PYTHON = VENV_BIN / "python"


def in_venv() -> bool:
    return Path(sys.prefix).resolve() == VENV_DIR.resolve()


def add_venv_to_path() -> None:
    """Put .venv/bin first on PATH for this process and any command it starts."""
    if VENV_BIN.is_dir() and str(VENV_BIN) not in os.environ.get("PATH", "").split(os.pathsep):
        os.environ["PATH"] = str(VENV_BIN) + os.pathsep + os.environ.get("PATH", "")


def reexec_in_venv() -> None:
    """Restart the current script with the venv's Python, if the venv exists."""
    if os.environ.get("CATALOGIX_NO_VENV") or in_venv() or not VENV_PYTHON.exists():
        add_venv_to_path()
        return
    add_venv_to_path()
    os.execv(str(VENV_PYTHON), [str(VENV_PYTHON), *sys.argv])
