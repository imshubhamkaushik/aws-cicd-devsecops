import sys
from pathlib import Path

# Support direct execution (python scripts/python/aws/<script>.py).
PYTHON_ROOT = Path(__file__).resolve().parents[1]
if str(PYTHON_ROOT) not in sys.path:
    sys.path.insert(0, str(PYTHON_ROOT))
from common.command import info, error, run_command


# The project root is three parent directories above this AWS entry point.
ROOT_DIR = Path(__file__).resolve().parents[3]

# Path Constants for backend_bootstrap terraform directory
BACKEND_BOOTSTRAP_DIR = ROOT_DIR / "infra" / "terraform" / "backend-bootstrap"


def backend_bootstrap():
    print("")
    info("Running Terraform Backend Bootstrap...")

    tfplan = BACKEND_BOOTSTRAP_DIR / "main.tfplan"

    env = {
        "AWS_MAX_ATTEMPTS": "10",
        "AWS_RETRY_MODE": "adaptive"
    }

    try:
        run_command("terraform init", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        run_command("terraform fmt -check", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
        run_command("terraform validate", cwd=BACKEND_BOOTSTRAP_DIR, env=env)

        # Check if apply is needed
        exit_code = run_command(
            "terraform plan -detailed-exitcode -out main.tfplan",
            cwd=BACKEND_BOOTSTRAP_DIR,
            env=env,
            check=False
        )

        # exit_code == 0  → no changes, skip apply
        # exit_code == 2  → changes detected, show plan, ask for confirmation, apply
        # anything else   → plan itself failed (bad config, auth error), call error()

        if exit_code == 0:
            info("No Terraform changes detected. Skipping apply.")
            return
        elif exit_code == 2:
            print("\nTerraform changes detected:")
            run_command("terraform show main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR, env=env)

            # Only ask for confirmation if changes exist
            confirm = input("\nProceed with Terraform apply for backend-bootstrap? (yes/no): ").strip().lower()
            if confirm not in ["yes", "y"]:
                info("Backend bootstrap aborted by user.")
                sys.exit(1)

            run_command("terraform apply main.tfplan", cwd=BACKEND_BOOTSTRAP_DIR, env=env)
            info("Backend Bootstrap Complete.")
            return
        else:
            error(f"Terraform plan failed with exit code {exit_code}! Check output above!")

    finally:
        if tfplan.exists():
            tfplan.unlink()
