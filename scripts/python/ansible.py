import getpass
from pathlib import Path
from utils.command import info, error, warn, run_command


ROOT_DIR = Path(__file__).resolve().parents[2]

ANSIBLE_DIR = ROOT_DIR / "ansible"

VAULT_PASSWORD_FILE = Path.home() / ".vault_pass"
VAULT_FILE = ANSIBLE_DIR / "group_vars" / "all" / "vault.yaml"


def _setup_vault_password_file():
    """Create ~/.vault_pass interactively if it doesn't exist."""
    if VAULT_PASSWORD_FILE.exists():
        info(f"Vault password file found at {VAULT_PASSWORD_FILE}")
        return
 
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
    result = run_command(
        f"ansible-vault encrypt {VAULT_FILE} --vault-password-file {VAULT_PASSWORD_FILE}"
    )
    if result.returncode != 0:
        VAULT_FILE.unlink(missing_ok=True)
        error("Failed to encrypt vault file. Check ansible-vault is installed.")
 
    info(f"\nVault file created and encrypted at {VAULT_FILE}")
 
 
def check_vault():
    """Ensure vault password file and vault file both exist, creating them if needed."""
    _setup_vault_password_file()
    _setup_vault_file()


# Ansible
def run_ansible():
    info("\nRunning Ansible Configuration...")
    
    print("\nChecking Ansible Vault setup...")    
    check_vault()
    
    print("\nRunning Ansible Galaxy collection install...")
    run_command("ansible-galaxy collection install -r requirements.yaml --force", cwd=ANSIBLE_DIR)
    
    print("\nRunning Ansible playbook...")
    run_command(
        f"ansible-playbook playbook.yaml --vault-password-file {VAULT_PASSWORD_FILE}",
        cwd=ANSIBLE_DIR
    )
    
    info("\nAnsible Configuration Complete.")
