import sys
from pathlib import Path
from utils.venv import reexec_in_venv

if __name__ == "__main__":
    reexec_in_venv()

from utils.command import info, warn, run_command
import allowlist
from bootstrap_infra import terraform_env

ROOT_DIR = Path(__file__).resolve().parents[2]

# Path Constants for bootstrap_infra terraform directory.
BOOTSTRAP_INFRA_DIR = ROOT_DIR / "infra" / "terraform" / "bootstrap-infra"


# Destroy Infrastructure
def destroy_infra():
    print("")
    info("Destroying Terraform Infrastructure...")

    tfplan = BOOTSTRAP_INFRA_DIR / "destroy.tfplan"

    env = terraform_env()

    try:
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        run_command("terraform plan -destroy -out destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        info("Destroy plan preview:")
        run_command("terraform show destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        confirm = input("This will DESTROY all infrastructure. Proceed with the destroy? (yes/no): ").strip().lower()

        if confirm not in ["yes", "y"]:
            info("Destroy aborted by user. Infrastructure destroy cancelled. No changes have been made.")
            sys.exit(0)

        run_command("terraform apply destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        info("Infrastructure successfully destroyed.")

        # The deployment is gone, so the IP list that guarded it goes too.
        try:
            allowlist.delete_all()
            info("Operator IP allowlist deleted.")
        except Exception as exc:  # the infrastructure is already destroyed; do not fail the run
            warn(f"Could not delete the IP allowlist ({exc}). Run: python3 scripts/python/allowlist.py delete")

    finally:
        if tfplan.exists():
            tfplan.unlink()

if __name__ == "__main__":
    destroy_infra()