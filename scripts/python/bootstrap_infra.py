import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]

BOOTSTRAP_INFRA_DIR = ROOT_DIR / "terraform" / "bootstrap-infra"
ANSIBLE_DIR = ROOT_DIR / "ansible"


def info(msg):
    print(f"[INFO] {msg}")


def error(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)


def run_command(cmd, cwd=None, capture_output=False):
    try:
        result = subprocess.run(
            cmd,
            cwd=cwd,
            shell=True,
            check=True,
            text=True,
            capture_output=capture_output
        )
        return result.stdout if capture_output else None
    except subprocess.CalledProcessError:
        error(f"Command failed: {cmd}")


def bootstrap_infra():
    print("")
    info("Running Bootstrap Infrastructure Terraform...")
    
    tfplan = BOOTSTRAP_INFRA_DIR / "main.tfplan"
    
    try:
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR)
        run_command("terraform fmt -check", cwd=BOOTSTRAP_INFRA_DIR)
        run_command("terraform validate", cwd=BOOTSTRAP_INFRA_DIR)
        run_command("terraform plan -out main.tfplan", cwd=BOOTSTRAP_INFRA_DIR)

        run_command("terraform show main.tfplan", cwd=BOOTSTRAP_INFRA_DIR)
        
        print("")
        confirm = input("Proceed with Terraform apply? (yes/no): ").strip().lower()

        if confirm not in ["yes", "y"]:
            info("Infra bootstrap aborted by user.")
            sys.exit(0)

        run_command("terraform apply main.tfplan", cwd=BOOTSTRAP_INFRA_DIR)
        
        print("")
        info("Infrastructure Bootstrap Complete.")

    finally:
        if tfplan.exists():
            tfplan.unlink()


def wait_for_ec2():
    print("")
    info("Waiting for EC2 instances to become healthy...")
    
    print("")
    jenkins_id = run_command(
        "terraform output -raw instance_id_jenkins",
        cwd=BOOTSTRAP_INFRA_DIR,
        capture_output=True
    ).strip()
    
    print("")
    sonarqube_id = run_command(
        "terraform output -raw instance_id_sonarqube",
        cwd=BOOTSTRAP_INFRA_DIR,
        capture_output=True
    ).strip()

    if not jenkins_id or not sonarqube_id:
        error("Could not read instance IDs from Terraform outputs.")

    instance_ids = f"{jenkins_id} {sonarqube_id}"

    run_command(f"aws ec2 wait instance-status-ok --instance-ids {instance_ids}")

    print("")
    info("EC2 instances healthy.")


def run_ansible():
    print("")
    info("Running Ansible Configuration...")
    
    print("")
    run_command("ansible-galaxy collection install -r requirements.yaml", cwd=ANSIBLE_DIR)
    run_command("ansible-playbook playbook.yaml", cwd=ANSIBLE_DIR)
    
    print("")
    info("Ansible Configuration Complete.")
