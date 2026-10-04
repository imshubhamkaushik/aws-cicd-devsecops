#!/usr/bin/env bash
#
# create-key-pair.sh: generates the SSH key pair for the Jenkins and SonarQube EC2 instances.
#
#   - Generated locally with ssh-keygen; Terraform only receives the public key (aws_key_pair).
#   - ed25519 rather than RSA.
#   - No passphrase: Ansible and Jenkins use the key non-interactively. Access is controlled by
#     the security group, which limits SSH to one IP. Do not reuse this key elsewhere.
#   - Stored in ~/.ssh/, outside the repository, so it cannot be committed by accident.
#
# Usage:
#   chmod +x create-key-pair.sh
#   ./create-key-pair.sh
#
set -euo pipefail

KEY_PATH="${HOME}/.ssh/catalogix-key"

log()  { echo -e "\n\033[1;34m==>\033[0m $*"; }
ok()   { echo -e "    \033[1;32m✓\033[0m $*"; }

mkdir -p "${HOME}/.ssh"
chmod 700 "${HOME}/.ssh"

if [[ -f "${KEY_PATH}" ]]; then
    log "A key already exists at ${KEY_PATH}"
    read -r -p "    Overwrite it? Anything using the old key pair will stop working. (yes/no): " confirm
    if [[ "${confirm}" != "yes" && "${confirm}" != "y" ]]; then
        echo "    Keeping the existing key. Nothing changed."
        exit 0
    fi
    rm -f "${KEY_PATH}" "${KEY_PATH}.pub"
fi

log "Generating ed25519 key pair (no passphrase — see comments in this script for why)"
ssh-keygen -t ed25519 -f "${KEY_PATH}" -C "catalogix-bootstrap" -N ""

# ssh-keygen already writes the private key at 0600; tighten to 0400
# (read-only, no write) since nothing should ever need to modify it.
chmod 400 "${KEY_PATH}"
chmod 644 "${KEY_PATH}.pub"

ok "Private key: ${KEY_PATH}  (mode 400, never leaves this machine)"
ok "Public key:   ${KEY_PATH}.pub  (mode 644, goes into terraform.tfvars)"

log "Next steps"
cat <<EOF

1. Add this to terraform/bootstrap-infra/terraform.tfvars:

     public_key = file("${KEY_PATH}.pub")

2. ansible.cfg already points at ${KEY_PATH} via:

     private_key_file = ~/.ssh/catalogix-key

   (no change needed if you used this script — that's the path it expects.)

3. Run terraform apply for bootstrap-infra. AWS only ever receives the
   public key; the private key in ${KEY_PATH} never leaves this machine,
   never enters Terraform state, and is excluded from git via .gitignore
   as a second layer of protection on top of "it was never in the repo
   directory to begin with."

More than one person needs access? Don't share this private key file —
see ansible/group_vars/all/team_keys.yaml and the README for the
per-person-key pattern, or look at AWS Systems Manager Session Manager
to remove the need for shared SSH keys entirely.

EOF
