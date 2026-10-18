import os
import re
import sys
from pathlib import Path

# Support direct execution (python scripts/python/aws/<script>.py).
PYTHON_ROOT = Path(__file__).resolve().parents[1]
if str(PYTHON_ROOT) not in sys.path:
    sys.path.insert(0, str(PYTHON_ROOT))
from common.command import info, error, run_command
import allowlist

ROOT_DIR = Path(__file__).resolve().parents[3]

# Path Constants for bootstrap_infra terraform directory.
BOOTSTRAP_INFRA_DIR = ROOT_DIR / "infra" / "terraform" / "bootstrap-infra"

SSH_PUBLIC_KEY = Path.home() / ".ssh" / "catalogix-key.pub"


def terraform_env():
    """Environment for terraform runs in bootstrap-infra.

    Supplies TF_VAR_public_key from ~/.ssh/catalogix-key.pub when the operator
    has not provided the key themselves (env var or terraform.tfvars).
    """

    env = {
        "AWS_MAX_ATTEMPTS": "10",
        "AWS_RETRY_MODE": "adaptive"
    }
    tfvars = BOOTSTRAP_INFRA_DIR / "terraform.tfvars"
    has_tfvars_key = tfvars.exists() and re.search(
        r"^\s*public_key\s*=", tfvars.read_text(encoding="utf-8"), re.MULTILINE
    ) is not None
    if not os.environ.get("TF_VAR_public_key") and not has_tfvars_key:
        if SSH_PUBLIC_KEY.exists():
            env["TF_VAR_public_key"] = SSH_PUBLIC_KEY.read_text(encoding="utf-8").strip()
        else:
            error(
                f"No SSH public key found. Run python3 scripts/python/setup/create_ssh_key.py (creates {SSH_PUBLIC_KEY}), "
                "or set TF_VAR_public_key, or put public_key in infra/terraform/bootstrap-infra/terraform.tfvars."
            )
    return env

# Terraform Execution with plan check and user confirmation.
def bootstrap_infra():
    print("")
    info("Running Bootstrap Infrastructure Terraform...")

    tfplan = BOOTSTRAP_INFRA_DIR / "main.tfplan"

    env = terraform_env()

    # Add this machine's IP to the shared allowlist if it is new (never replaces
    # existing entries) and hand the whole list to Terraform.
    try:
        allowlist.sync()
    except allowlist.AllowlistError as exc:
        error(f"Allowlist step failed: {exc}")

    try:
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        run_command("terraform fmt -check", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        run_command("terraform validate", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        # Check if apply is needed
        exit_code = run_command(
            "terraform plan -detailed-exitcode -out main.tfplan",
            cwd=BOOTSTRAP_INFRA_DIR,
            env=env,
            check=False
        )

        if exit_code == 0:
            info("No Terraform changes detected. Skipping apply.")
            return
        elif exit_code == 2:
            print("\nTerraform changes detected:")
            run_command("terraform show main.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

            # Only ask for confirmation if changes exist
            confirm = input("\nProceed with Terraform apply for bootstrap-infra? (yes/no): ").strip().lower()
            if confirm not in ["yes", "y"]:
                info("Bootstrap infra aborted by user.")
                sys.exit(1)

            run_command("terraform apply main.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)
            info("Bootstrap Infrastructure Complete.")
            return
        else:
            error(f"Terraform plan failed with exit code {exit_code}! Check output above!")

    finally:
        if tfplan.exists():
            tfplan.unlink()


def wait_for_ec2():

    print("")
    info("Waiting for EC2 instances to become healthy...")

    print("")
    print("Checking Terraform outputs for instance IDs...")

    print("")
    print("Terraform outputs:")

    # Read instance IDs from terraform output so re-created instances are picked up and no ID is hardcoded.
    jenkins_output = run_command(
        "terraform output -raw instance_id_jenkins",
        cwd=BOOTSTRAP_INFRA_DIR,
        capture_output=True,
    )

    if not isinstance(jenkins_output, str):
        error("Failed to read Jenkins instance ID from Terraform output.")

    jenkins_id = jenkins_output.strip()

    sonarqube_output = run_command(
        "terraform output -raw instance_id_sonarqube",
        cwd=BOOTSTRAP_INFRA_DIR,
        capture_output=True,
    )

    if not isinstance(sonarqube_output, str):
        error("Failed to read SonarQube instance ID from Terraform output.")

    sonarqube_id = sonarqube_output.strip()

    if not jenkins_id or not sonarqube_id:
        error("Could not read instance IDs from Terraform outputs.")

    print(f"  Jenkins Instance ID: {jenkins_id}")
    print(f"  SonarQube Instance ID: {sonarqube_id}")

    instance_ids = f"{jenkins_id} {sonarqube_id}"

    # Wait for both instances to be in the 'ok' status before proceeding.
    print("")
    print("Waiting for EC2 instances status to be 'ok'...")
    run_command(f"aws ec2 wait instance-status-ok --instance-ids {instance_ids}")
    print("EC2 instances 'OK' status checks passed.")

    # Wait for both instances to be 'running' so they accept SSH when Ansible connects later.
    print("")
    print("Waiting for EC2 instances status to be 'running' so they accept SSH when Ansible connects later...")
    run_command(f"aws ec2 wait instance-running --instance-ids {instance_ids}")
    print("EC2 instance running checks passed.")

    print("")
    info("EC2 instances are healthy. Ready for Ansible configuration.")
