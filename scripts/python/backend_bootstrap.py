import subprocess
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[2]

BACKEND_BOOTSTRAP_DIR = ROOT_DIR / "terraform" / "backend-bootstrap"


def info(msg):
    print(f"[INFO] {msg}")


def error(msg):
    print(f"[ERROR] {msg}")
    sys.exit(1)


def run_command(cmd, cwd=None):
    try:
        subprocess.run(cmd, cwd=cwd, shell=True, check=True)
    except subprocess.CalledProcessError:
        error(f"Command failed: {cmd}")


def backend_bootstrap():
    print("")
    info("Running Terraform Backend Bootstrap...")
    
    tfplan = BACKEND_BOOTSTRAP_DIR / "main.tfplan"

    try:
        run_command("terraform init", cwd=BACKEND_BOOTSTRAP_DIR)
        run_command("terraform fmt -check", cwd=BACKEND_BOOTSTRAP_DIR)
        run_command("terraform validate", cwd=BACKEND_BOOTSTRAP_DIR)
        run_command("terraform plan -out main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR)
    
        run_command("terraform show main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR)
        
        print("")    
        confirm = input("Proceed with Terraform apply? (yes/no): ").strip().lower()
    
        if confirm not in ["yes", "y"]:
            info("Backend bootstrap aborted.")
            sys.exit(0)

        run_command("terraform apply main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR)
        
        print("")
        info("Backend Bootstrap Complete.")

    finally:
        if tfplan.exists():
            tfplan.unlink()

    