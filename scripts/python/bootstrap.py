import argparse
import shutil
import subprocess
import sys

from utils.command import info, error, warn, run_command
from backend_bootstrap import backend_bootstrap
from bootstrap_infra import bootstrap_infra, wait_for_ec2
from ansible import run_ansible


DEPENDENCIES = [
    {
        "name":       "terraform",
        "cli_tool":   "terraform",
        "py_module":  None,
        "install":    (
            "sudo apt-get install -y gnupg software-properties-common && "
            "wget -qO- https://apt.releases.hashicorp.com/gpg | "
            "sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg && "
            "echo \"deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] "
            "https://apt.releases.hashicorp.com $(lsb_release -cs) main\" | "
            "sudo tee /etc/apt/sources.list.d/hashicorp.list && "
            "sudo apt-get update && sudo apt-get install -y terraform"
        ),
        "help":       "Install from https://developer.hashicorp.com/terraform/install",
    },
    {
        "name":       "aws (AWS CLI)",
        "cli_tool":   "aws",
        "py_module":  None,
        "install":    (
            "curl -fsSL https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip "
            "-o /tmp/awscliv2.zip && "
            "unzip -q /tmp/awscliv2.zip -d /tmp && "
            "sudo /tmp/aws/install && "
            "rm -rf /tmp/awscliv2.zip /tmp/aws"
        ),
        "help":       "Install from https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html",
    },
    {
        "name":       "ansible-playbook",
        "cli_tool":   "ansible-playbook",
        "py_module":  None,
        "install":    "sudo apt-get install -y ansible",
        "help":       "Run: sudo apt-get install -y ansible",
    },
    {
        "name":       "ansible-galaxy",
        "cli_tool":   "ansible-galaxy",
        "py_module":  None,
        "install":    "sudo apt-get install -y ansible",
        "help":       "Installed alongside ansible: sudo apt-get install -y ansible",
    },
    {
        "name":       "jq",
        "cli_tool":   "jq",
        "py_module":  None,
        "install":    "sudo apt-get install -y jq",
        "help":       "Run: sudo apt-get install -y jq",
    },
    {
        "name":       "boto3 (Python)",
        "cli_tool":   None,
        "py_module":  "boto3",
        "install":    "sudo apt-get install -y python3-pip python3-boto3 python3-botocore",
        "help":       "Run: sudo apt-get install -y python3-boto3 python3-botocore",
    },
]


def _is_missing(dep):
    """Return True if the dependency is not available."""
    if dep["cli_tool"] and shutil.which(dep["cli_tool"]) is None:
        return True
    if dep["py_module"]:
        # Use subprocess directly — run_command exits on failure which would
        # abort the whole check loop instead of just marking the dep as missing.
        result = subprocess.run(
            f"python3 -c 'import {dep['py_module']}'",
            shell=True,
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
    confirm = input("Auto-install all missing dependencies? (yes/no): ").strip().lower()
 
    if confirm not in ["yes", "y"]:
        print("")
        info("Manual install commands:")
        for dep in missing:
            print(f"       {dep['name']}: {dep['help']}")
        error("Please install missing dependencies and re-run.")
 
    # apt-get based installs benefit from a single update pass first
    apt_needed = any("apt-get" in dep["install"] for dep in missing)
    if apt_needed:
        info("Updating apt package index...")
        run_command("sudo apt-get update -qq")
 
    for dep in missing:
        info(f"Installing {dep['name']}...")
        info(f"If this fails, install manually: {dep['help']}")
        run_command(dep["install"])
 
    # Verify everything is now present after installation
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
    info("Full Bootstrap Completed Successfully.")


def main():
    parser = argparse.ArgumentParser(
        description="Infrastructure Bootstrap Automation Tool"
    )

    parser.add_argument(
        "command",
        nargs="?",
        default="full",
        choices=["backend", "infra", "ansible", "full"],
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
        
    # For full bootstrap (backend + infra + ansible)
    elif args.command == "full":
        run_full_bootstrap()


if __name__ == "__main__":
    main()