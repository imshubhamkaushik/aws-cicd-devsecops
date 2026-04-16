import argparse
import shutil
import subprocess
import sys

from backend_bootstrap import backend_bootstrap
from bootstrap_infra import bootstrap_infra, wait_for_ec2, run_ansible


def error(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)
    

def info(msg):
    print(f"[INFO] {msg}")


def check_dependency(dep):
    if shutil.which(dep) is None:
        error(f"{dep} not installed.")


def check_dependencies():
    for dep in [
        "terraform",
        "aws",
        "ansible-playbook",
        "ansible-galaxy",
        "jq"
    ]:
        check_dependency(dep)


def check_aws_auth():
    result = subprocess.run(
        "aws sts get-caller-identity",
        shell=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )

    if result.returncode != 0:
        error("AWS credentials invalid/not configured.")


def run_full__bootstrap():

    backend_bootstrap()

    confirm = input(
        "Proceed to infrastructure bootstrap? (yes/no): "
    ).strip().lower()

    if confirm not in ["yes", "y"]:
        sys.exit(0)

    bootstrap_infra()
    wait_for_ec2()
    run_ansible()

    print("[INFO] Full Bootstrap Completed Successfully.")


def main():
    parser = argparse.ArgumentParser(
        description="Infrastructure Bootstrap Automation Tool"
    )

    parser.add_argument(
        "command",
        nargs="?",
        default="full",
        choices=["backend", "infra", "full"],
        help="Command to run (default: full)"
    )

    args = parser.parse_args()

    check_dependencies()
    check_aws_auth()

    if args.command == "backend":
        backend_bootstrap()

    elif args.command == "infra":
        bootstrap_infra()
        wait_for_ec2()
        run_ansible()

    elif args.command == "full":
        run_full__bootstrap()


if __name__ == "__main__":
    main()