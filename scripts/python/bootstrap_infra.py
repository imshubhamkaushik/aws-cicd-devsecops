import sys
from pathlib import Path
from utils.command import info, error, warn, run_command

ROOT_DIR = Path(__file__).resolve().parents[2]

BOOTSTRAP_INFRA_DIR = ROOT_DIR / "terraform" / "bootstrap-infra"

# Terraform
def bootstrap_infra():
    print("")
    info("Running Bootstrap Infrastructure Terraform...")
    
    tfplan = BOOTSTRAP_INFRA_DIR / "main.tfplan"
    
    env = {
        "AWS_MAX_ATTEMPTS": "10",
        "AWS_RETRY_MODE": "adaptive"
    }
    
    try:
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        run_command("terraform fmt -check", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        run_command("terraform validate", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        run_command("terraform plan -out main.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        
        print("")
        print("Performing Terraform plan review...")
        run_command("terraform show main.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        
        print("")
        confirm = input("Proceed with Terraform apply? (yes/no): ").strip().lower()

        if confirm not in ["yes", "y"]:
            info("Infra bootstrap aborted by user.")
            sys.exit(0)
        
        print("")
        print("Performing Terraform apply...")
        run_command("terraform apply main.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)
        
        print("")
        info("Infrastructure Bootstrap Complete.")

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
    
    jenkins_id = run_command(
        "terraform output -raw instance_id_jenkins",
        cwd=BOOTSTRAP_INFRA_DIR,
        capture_output=True
    ).strip()
    
    sonarqube_id = run_command(
        "terraform output -raw instance_id_sonarqube",
        cwd=BOOTSTRAP_INFRA_DIR,
        capture_output=True
    ).strip()

    if not jenkins_id or not sonarqube_id:
        error("Could not read instance IDs from Terraform outputs.")
        
    print(f"  Jenkins Instance ID: {jenkins_id}")
    print(f"  SonarQube Instance ID: {sonarqube_id}")

    instance_ids = f"{jenkins_id} {sonarqube_id}"

    run_command(f"aws ec2 wait instance-status-ok --instance-ids {instance_ids}")

    print("")
    info("EC2 instances healthy.")
