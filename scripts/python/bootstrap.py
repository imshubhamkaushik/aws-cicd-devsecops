import argparse
import shutil
import subprocess
import sys
from pathlib import Path

from utils.venv import reexec_in_venv

if __name__ == "__main__":
    # Run inside the project virtualenv (.venv) if install_tools.py has created it.
    reexec_in_venv()

from utils.command import info, error, warn, run_command
from backend_bootstrap import backend_bootstrap
from bootstrap_infra import bootstrap_infra, wait_for_ec2
from ansible_runner import run_ansible
from credentials import main as credentials_main, CLUSTER_NAMES


# What the bootstrap needs. Anything missing is installed by install_tools.py (stdlib-only Python;
# works on apt, dnf, yum and zypper systems), so there are no distro-specific commands here.
DEPENDENCIES = [
    {"name": "terraform",        "cli_tool": "terraform",        "py_module": None},
    {"name": "aws (AWS CLI)",    "cli_tool": "aws",              "py_module": None},
    {"name": "ansible-playbook", "cli_tool": "ansible-playbook", "py_module": None},
    {"name": "ansible-galaxy",   "cli_tool": "ansible-galaxy",   "py_module": None},
    {"name": "jq",               "cli_tool": "jq",               "py_module": None},
    {"name": "boto3 (Python)",   "cli_tool": None,               "py_module": "boto3"},
]

INSTALLER = Path(__file__).resolve().parent / "install_tools.py"


def _is_missing(dep):
    """Return True if the dependency is not available."""
    if dep["cli_tool"] and shutil.which(dep["cli_tool"]) is None:
        return True
    if dep["py_module"]:
        # Use subprocess directly: run_command exits on failure, which would abort the
        # whole check loop instead of just marking this dependency as missing.
        result = subprocess.run(
            [sys.executable, "-c", f"import {dep['py_module']}"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return result.returncode != 0
    return False


def check_dependencies():
    missing = [dep for dep in DEPENDENCIES if _is_missing(dep)]

    if not missing:
        info("All dependencies satisfied.")
        return

    print("")
    warn("The following dependencies are missing:")
    for dep in missing:
        print(f"       - {dep['name']}")

    print("")
    confirm = input("Install them now with scripts/python/install_tools.py? (yes/no): ").strip().lower()

    if confirm not in ["yes", "y"]:
        print("")
        info(f"Install them yourself with: bash scripts/bash/install-all.sh  (or: python3 {INSTALLER})")
        error("Please install missing dependencies and re-run.")

    run_command(f"{sys.executable} {INSTALLER}")

    # install_tools.py may just have created .venv (Ansible and boto3 live there). Restart this
    # script inside it so the rest of the run sees them.
    reexec_in_venv()

    still_missing = [dep for dep in missing if _is_missing(dep)]
    if still_missing:
        names = ", ".join(d["name"] for d in still_missing)
        error(f"Installation appeared to succeed but {names} still not found. Check the output above.")

    print("")
    info("All dependencies installed successfully.")


def check_aws_auth():
    # Use subprocess directly — we want to suppress output without affecting
    # run_command's retry/error logic for a simple auth check.
    result = subprocess.run(
        "aws sts get-caller-identity",
        shell=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        error("AWS credentials invalid or not configured. Run: aws configure")


def run_credentials(env=None):
    """Prompt for the operator-chosen credentials and store them in AWS Secrets Manager.

    This is the handoff to everything that runs later: the platform-infra pipeline (Terraform on
    the Jenkins host), External Secrets and the app pipeline all READ the secret this writes —
    nothing has to be passed to them. See credentials.py for the details.
    """
    if env is None:
        choices = "/".join(sorted(CLUSTER_NAMES))
        env = input(f"Which environment are these credentials for? ({choices}) [dev]: ").strip().lower() or "dev"
        if env not in CLUSTER_NAMES:
            error(f"Unknown environment '{env}'.")
    if credentials_main(["--env", env]) != 0:
        error("Credentials were not saved. Re-run:  python3 scripts/python/bootstrap.py credentials")


def run_full_bootstrap():

    backend_bootstrap()

    print("")
    confirm_infra = input(
        "Ready to bootstrap infrastructure. Proceed to infrastructure bootstrap? (yes/no): "
    ).strip().lower()

    if confirm_infra not in ["yes", "y"]:
        sys.exit(0)

    bootstrap_infra()
    wait_for_ec2()

    run_ansible()

    print("")
    info("Next: the passwords YOU choose for the application (RDS, RabbitMQ, app admin, Grafana).")
    run_credentials()

    print("")
    info("Full Bootstrap Completed Successfully.")
    info("Now run the platform-infra pipeline in Jenkins.")


def main():
    parser = argparse.ArgumentParser(
        description="Infrastructure Bootstrap Automation Tool"
    )

    parser.add_argument(
        "command",
        nargs="?",
        default="full",
        choices=["backend", "infra", "ansible", "credentials", "full"],
        help="Command to run (default: full)"
    )

    args = parser.parse_args()

    check_dependencies()
    check_aws_auth()

    # Run the appropriate command based on user input
    # For backend bootstrap
    if args.command == "backend":
        backend_bootstrap()

    # For infra bootstrap
    elif args.command == "infra":
        bootstrap_infra()
        wait_for_ec2()

    # For Ansible configuration
    elif args.command == "ansible":
        run_ansible()

    # For the operator-chosen application credentials (re-run any time to change a password)
    elif args.command == "credentials":
        run_credentials()

    # For full bootstrap (backend + infra + ansible + credentials)
    elif args.command == "full":
        run_full_bootstrap()


if __name__ == "__main__":
    main()
