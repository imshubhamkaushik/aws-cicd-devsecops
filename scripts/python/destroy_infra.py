import sys
from pathlib import Path
from utils.command import info, error, run_command

ROOT_DIR = Path(__file__).resolve().parents[2]

BOOTSTRAP_INFRA_DIR = ROOT_DIR / "terraform" / "bootstrap-infra"


# Destroy Infrastructure
def destroy_infra():
    print("")
    info("Destroying Terraform Infrastructure...")

    tfplan = BOOTSTRAP_INFRA_DIR / "destroy.tfplan"
    
    env = {
        "AWS_MAX_ATTEMPTS": "10",
        "AWS_RETRY_MODE": "adaptive"
    }

    try:
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        run_command("terraform plan -destroy -out destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        info("Destroy plan preview:")
        run_command("terraform show destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        confirm = input("This will DESTROY all infrastructure. Proceed with the destroy? (yes/no): ").strip().lower()

        if confirm not in ["yes", "y"]:
            info("Destroy aborted by user.")
            error("Infrastructure destroy cancelled. No changes have been made.")
            sys.exit(0)

        run_command("terraform apply destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        info("Infrastructure successfully destroyed.")

    finally:
        if tfplan.exists():
            tfplan.unlink()

if __name__ == "__main__":
    destroy_infra()