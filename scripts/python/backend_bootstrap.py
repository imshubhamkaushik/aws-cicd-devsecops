import sys
from pathlib import Path
from utils.command import info, error, warn, run_command


ROOT_DIR = Path(__file__).resolve().parents[2]

BACKEND_BOOTSTRAP_DIR = ROOT_DIR / "terraform" / "backend-bootstrap"


def backend_bootstrap():
    info("\nRunning Terraform Backend Bootstrap...")
    
    tfplan = BACKEND_BOOTSTRAP_DIR / "main.tfplan"
    
    env={
        "AWS_MAX_ATTEMPTS": "10",
        "AWS_RETRY_MODE": "adaptive"
    }

    try:
        run_command("terraform init", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        run_command("terraform fmt -check", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        run_command("terraform validate", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        run_command("terraform plan -out main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        
        print("")
        print("Performing Terraform plan review...")    
        run_command("terraform show main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
           
        print("")
        confirm = input("Proceed with Terraform apply? (yes/no): ").strip().lower()
    
        if confirm not in ["yes", "y"]:
            info("\nBackend bootstrap aborted.")
            sys.exit(0)

        run_command("terraform apply main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        
        print("")
        info("Backend Bootstrap Complete.")

    finally:
        if tfplan.exists():
            tfplan.unlink()

    