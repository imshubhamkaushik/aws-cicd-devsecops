import sys
from pathlib import Path
from utils.command import info, error, run_command


# __file__ is the path of the current Python file.
# Path(__file__) - Converts the string path into a pathlib.Path object. Result: PosixPath('/home/shubham/project/app/utils/helper.py')
# .resolve() - Converts the path into an absolute canonical path. Result: /home/shubham/project/app/utils/helper.py
# .parents - parents is a sequence of parent directories.
# .parent      # single immediate parent directory
# .parents[0]  # same as .parent
# .parents[1]  # one more level up
# .parents[2]  # two more levels up
# Suppose: /home/shubham/project/app/utils/helper.py --> Then: Path(__file__).resolve().parent --> gives: /home/shubham/project/app/utils
# parents[0] = /home/user/project/app/utils
# parents[1] = /home/user/project/app
# parents[2] = /home/user/project
# parents[3] = /home/user
# parents[4] = /home
# parents[5] = /
ROOT_DIR = Path(__file__).resolve().parents[2]

# Path Constants for backend_bootstrap terraform directory
BACKEND_BOOTSTRAP_DIR = ROOT_DIR / "terraform" / "backend-bootstrap"


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
    