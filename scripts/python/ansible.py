import getpass
import subprocess
from pathlib import Path
from utils.command import info, error, warn, run_command


ROOT_DIR = Path(__file__).resolve().parents[2]

ANSIBLE_DIR = ROOT_DIR / "ansible"

VAULT_PASSWORD_FILE = Path.home() / ".vault_pass"
VAULT_FILE = ANSIBLE_DIR / "group_vars" / "all" / "vault.yaml"


# Vault Handling
def _is_vault_password_valid():
    """Check if current vault password can decrypt vault.yaml."""
    if not VAULT_FILE.exists():
        return True

    result = subprocess.run(
        f"ansible-vault view {VAULT_FILE} --vault-password-file {VAULT_PASSWORD_FILE}",
        shell=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0

def _prompt_for_vault_password():
    while True:
        password = getpass.getpass("Enter vault password: ")
        if password.strip():
            VAULT_PASSWORD_FILE.write_text(password)
            VAULT_PASSWORD_FILE.chmod(0o600)
            return
        warn("Password cannot be empty. Try again.")


def _create_vault_password_file():
    print("")
    warn("Vault password file not found at ~/.vault_pass")
    warn("This file stores the password used to encrypt/decrypt your secrets vault.")
    print("")
    confirm = input("Create ~/.vault_pass now? (yes/no): ").strip().lower()
    if confirm not in ["yes", "y"]:
        error(
            "Vault password file is required to run Ansible.\n"
            f"       Create it manually: echo 'your-password' > {VAULT_PASSWORD_FILE} && chmod 600 {VAULT_PASSWORD_FILE}"
        )

    password = getpass.getpass("Enter vault password: ")
    confirm_password = getpass.getpass("Confirm vault password: ")

    if password != confirm_password:
        error("Passwords do not match. Re-run and try again.")

    if not password.strip():
        error("Vault password cannot be empty.")

    VAULT_PASSWORD_FILE.write_text(password)
    VAULT_PASSWORD_FILE.chmod(0o600)

    print("")
    info(f"Vault password file created at {VAULT_PASSWORD_FILE}")


def _validate_existing_password_file():
    if not VAULT_PASSWORD_FILE.exists():
        warn("Vault password file missing but vault.yaml exists.")
        return False

    info(f"Vault password file found at {VAULT_PASSWORD_FILE}")
    if _is_vault_password_valid():
        info("Vault password is valid.")
        return True

    warn("Vault password does NOT match vault file.")
    return False


def _handle_invalid_vault_password():
    attempts = 0
    while True:
        print("")
        _prompt_for_vault_password()

        if _is_vault_password_valid():
            info("Vault password is valid.")
            return True

        attempts += 1
        warn("Incorrect vault password. Try again.")

        if attempts >= 3:
            print("")
            warn("Unable to decrypt vault. Password may be incorrect or lost.")
            confirm = input(
                "Recreate vault? This will DELETE existing secrets. (yes/no): "
            ).strip().lower()

            if confirm in ["yes", "y"]:
                VAULT_FILE.unlink(missing_ok=True)
                VAULT_PASSWORD_FILE.unlink(missing_ok=True)
                info("Old vault deleted. Starting fresh setup.")
                return False

            error("Cannot proceed without valid vault password.")


def _setup_vault_password_file():
    """Ensure ~/.vault_pass exists and is valid."""

    if VAULT_FILE.exists():
        if _validate_existing_password_file():
            return

        vaut_valid = _handle_invalid_vault_password()
        
        if not vaut_valid:
            _create_vault_password_file()
        return

    if not VAULT_PASSWORD_FILE.exists():
        _create_vault_password_file()
 
# Vault File Setup
def _prompt_secret(prompt, allow_empty=False):
    """Prompt for a secret using getpass (input is hidden)."""
    while True:
        value = getpass.getpass(f"  {prompt}: ")
        if value.strip() or allow_empty:
            return value.strip()
        warn("Value cannot be empty. Try again.")
 
 
def _setup_vault_file():
    """Create and encrypt group_vars/all/vault.yaml interactively if it doesn't exist."""
    if VAULT_FILE.exists():
        info(f"Vault file found at {VAULT_FILE}")
        return
 
    print("")
    warn(f"Vault file not found at {VAULT_FILE}")
    warn("This file holds all encrypted secrets used by Ansible.")
    
    print("")
    confirm = input("Create and encrypt vault file now? (yes/no): ").strip().lower()
    if confirm not in ["yes", "y"]:
        error(
            "Vault file is required to run Ansible.\n"
            "       Create it manually: ansible-vault create ansible/group_vars/all/vault.yaml"
        )
 
    # Ensure the directory exists
    VAULT_FILE.parent.mkdir(parents=True, exist_ok=True)
 
    print("")
    info("Enter your secret values below (input is hidden):")
    print("")
 
    secrets = {
        "vault_jenkins_admin_password": _prompt_secret("Jenkins admin password"),
        "vault_github_token":           _prompt_secret("GitHub personal access token"),
        "vault_aws_access_key_id":      _prompt_secret("AWS access key ID"),
        "vault_aws_secret_access_key":  _prompt_secret("AWS secret access key"),
        "vault_sonar_admin_password":   _prompt_secret("SonarQube admin password"),
        # Populated automatically by the sonarqube role on first run
        "vault_sonar_token":            "",
    }
 
    # Write plaintext vault file
    lines = [
        "# Shared vault — loaded for ALL hosts (group_vars/all/vault.yaml)",
        "# ANSIBLE MANAGED — edit with: ansible-vault edit group_vars/all/vault.yaml",
        "",
    ]
    for key, value in secrets.items():
        if key == "vault_sonar_token":
            lines.append("# Populated automatically by sonarqube role on first run")
        lines.append(f'{key}: "{value}"')
 
    VAULT_FILE.write_text("\n".join(lines) + "\n")
 
    # Encrypt it immediately
    result = subprocess.run(
        f"ansible-vault encrypt {VAULT_FILE} --vault-password-file {VAULT_PASSWORD_FILE}",
        shell=True,
    )
    
    if result.returncode != 0:
        VAULT_FILE.unlink(missing_ok=True)
        error("Failed to encrypt vault file. Check ansible-vault is installed.")
        
    print("")
    info(f"Vault file created and encrypted at {VAULT_FILE}")
 
 
def check_vault():
    """Ensure vault password file and vault file both exist, creating them if needed."""
    _setup_vault_password_file()
    _setup_vault_file()


# Ansible
def run_ansible():
    print("")
    info("Running Ansible Configuration...")
    
    print("")
    confirm = input("Ready to run Ansible playbook. Proceed with Ansible configuration? (yes/no): ").strip().lower()
    
    if confirm not in ["yes", "y"]:
        info("Ansible configuration aborted by user.")
        return
    
    print("")
    print("Checking Ansible Vault setup...")    
    check_vault()
    
    print("")
    print("Running Ansible Galaxy collection install...")
    run_command("ansible-galaxy collection install -r requirements.yaml --force", cwd=ANSIBLE_DIR)
    
    print("")
    print("Running Ansible playbook...")
    run_command(
        f"ansible-playbook playbook.yaml --vault-password-file {VAULT_PASSWORD_FILE}",
        cwd=ANSIBLE_DIR
    )
    
    print("")
    info("Ansible Configuration Complete.")
