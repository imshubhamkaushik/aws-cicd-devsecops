#!/usr/bin/env bash
#
# create-key-pair.sh — generates the SSH key pair used to reach the Jenkins
# and SonarQube EC2 instances, following the approach already documented
# (but not automated) in terraform/bootstrap-infra/key-pair.tf:
#
#   - Generated LOCALLY with ssh-keygen — Terraform never sees the private
#     half. The old approach (tls_private_key + local_sensitive_file) put
#     the private key into the Terraform state file in plaintext, readable
#     by anyone with s3:GetObject on the state bucket — which today includes
#     Jenkins's own IAM role. aws_key_pair only ever needs the PUBLIC key.
#   - ed25519, not RSA — shorter, faster, and the modern default recommended
#     by OpenSSH itself since 6.5 (2014). No real reason to default to RSA
#     in 2026 unless something you connect to specifically requires it.
#   - No passphrase (-N ""). This key is used non-interactively by Ansible
#     and by Jenkins (as a jump host to SonarQube) — a passphrase would mean
#     either hardcoding it somewhere (defeats the point) or running an
#     ssh-agent on a CI box (extra moving part for a single-purpose key).
#     The actual access control for this key is the security group, which
#     locks SSH to one IP at apply time — see bootstrap-infra/security-groups.tf.
#     Don't reuse this key for anything else, and don't widen that security
#     group without understanding that tradeoff.
#   - Stored in ~/.ssh/, not inside the git repo. Belt-and-suspenders over
#     .gitignore: a key that never exists inside the repo directory can't
#     end up in a commit by accident, regardless of what's excluded.
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
