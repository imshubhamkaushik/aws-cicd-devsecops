import subprocess
import sys
from pathlib import Path
from utils.command import info, error, run_command

ROOT_DIR = Path(__file__).resolve().parents[2]

BOOTSTRAP_INFRA_DIR = ROOT_DIR / "terraform" / "bootstrap-infra"


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
    except subprocess.CalledProcessError as e:
        print(e.stdout)
        print(e.stderr)
        error(f"Command failed: {cmd}")


# Destroy Infrastructure
def destroy_infra():
    info("\nDestroying Terraform Infrastructure...")

    tfplan = BOOTSTRAP_INFRA_DIR / "destroy.tfplan"
    
    env = {
        "AWS_MAX_ATTEMPTS": "10",
        "AWS_RETRY_MODE": "adaptive"
    }

    try:
        # Init (safe to run multiple times)
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        # Create destroy plan
        run_command("terraform plan -destroy -out destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        info("Destroy plan preview:")
        run_command("terraform show destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        confirm = input("This will DELETE all infrastructure. Proceed? (yes/no): ").strip().lower()

        if confirm not in ["yes", "y"]:
            info("Destroy aborted by user.")
            sys.exit(0)

        # Apply destroy
        run_command("terraform apply destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR, env=env)

        print("")
        info("Infrastructure successfully destroyed.")

    finally:
        # Cleanup plan file
        if tfplan.exists():
            tfplan.unlink()

if __name__ == "__main__":
    destroy_infra()