import subprocess
import sys
from pathlib import Path

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


# ==============================
# Destroy Infrastructure
# ==============================
def destroy_infra():
    print("")
    info("Destroying Terraform Infrastructure...")

    tfplan = BOOTSTRAP_INFRA_DIR / "destroy.tfplan"

    try:
        # Init (safe to run multiple times)
        run_command("terraform init", cwd=BOOTSTRAP_INFRA_DIR)

        # Create destroy plan
        run_command("terraform plan -destroy -out destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR)

        print("")
        info("Destroy plan preview:")
        run_command("terraform show destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR)

        print("")
        confirm = input("⚠️  This will DELETE all infrastructure. Proceed? (yes/no): ").strip().lower()

        if confirm not in ["yes", "y"]:
            info("Destroy aborted by user.")
            sys.exit(0)

        # Apply destroy
        run_command("terraform apply destroy.tfplan", cwd=BOOTSTRAP_INFRA_DIR)

        print("")
        info("Infrastructure successfully destroyed.")

    finally:
        # Cleanup plan file
        if tfplan.exists():
            tfplan.unlink()


# ==============================
# Optional: Wait for termination
# ==============================
def wait_for_termination():
    print("")
    info("Checking for remaining EC2 instances...")

    try:
        instance_ids = run_command(
            "terraform output -raw instance_id_jenkins",
            cwd=BOOTSTRAP_INFRA_DIR,
            capture_output=True
        ).strip()

        if instance_ids:
            run_command(f"aws ec2 wait instance-terminated --instance-ids {instance_ids}")

    except Exception:
        # Outputs may not exist after destroy → ignore
        pass

    info("All resources cleaned up.")


if __name__ == "__main__":
    destroy_infra()
    wait_for_termination()