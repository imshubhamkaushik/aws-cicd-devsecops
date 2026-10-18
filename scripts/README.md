# Catalogix scripts

Catalogix keeps scripts grouped by **language and target**:

```text
scripts/
├── bash/                      # minimal bootstrap only
│   ├── install-python.sh      # install Python on supported Linux distros
│   └── install-all.sh         # delegate tool checks/install to Python
└── python/
    ├── install_tools.py       # inventory + interactive/selective tool installation
    ├── setup/                 # workstation identity/key setup
    ├── local/                 # local build, scan, Compose and smoke-test commands
    ├── aws/                   # AWS bootstrap, Terraform orchestration, credentials, allowlist
    ├── ci/                    # CI contract checks expressed in Python
    ├── common/                # shared command and virtualenv helpers
    └── tests/                 # offline unit tests for operator tooling
```

Run commands from the repository root unless stated otherwise. All Python entry points remain independent: use the specific command needed rather than the full local gate.

## 1. Machine setup and tool inventory

```bash
bash scripts/bash/install-all.sh
```

The bootstrap first ensures Python 3 is available, then `install_tools.py` checks the managed tools: `jq`, OpenSSH (`ssh-keygen`), AWS CLI v2, Terraform, and Ansible plus `boto3` in the repository `.venv` (including required Ansible Galaxy collections).

When something is missing, the installer offers to install **all missing tools**, select a subset, or quit. Tool-specific installers remain functions within `install_tools.py` so detection, version display, elevation, download verification, and error handling stay in one place; separate one-file Python installers would mostly duplicate those concerns.

Useful alternatives:

```bash
python3 scripts/python/install_tools.py --check-only
python3 scripts/python/install_tools.py --only terraform,aws
python3 scripts/python/install_tools.py --yes                 # install all missing tools without a prompt
python3 scripts/python/install_tools.py --terraform-version 1.12.2
```

`--only` accepts any combination of `jq`, `ssh`, `aws`, `terraform`, and `ansible`. The installer targets Linux (including WSL). It does not install Docker Desktop, Maven, Node.js, kubectl, or Helm; install those separately for your local workflows. The Python scripts automatically re-execute inside `.venv` when that environment exists.

Create the SSH key used by the EC2 bootstrap with:

```bash
python3 scripts/python/setup/create_ssh_key.py
```

It writes `~/.ssh/catalogix-key` and its public key. Existing keys are preserved unless replacement is confirmed; use `--force` only when rotation is intentional.

## 2. AWS/operator commands

These commands target AWS or manage bootstrap infrastructure; they are not local-only development commands.

```bash
python3 scripts/python/aws/bootstrap.py [backend|infra|ansible|credentials|full]
python3 scripts/python/aws/credentials.py --env dev
python3 scripts/python/aws/credentials.py --env dev --check
python3 scripts/python/aws/credentials.py --env dev --show
python3 scripts/python/aws/credentials.py --env dev --only seed_admin_password
python3 scripts/python/aws/allowlist.py sync
python3 scripts/python/aws/allowlist.py list
python3 scripts/python/aws/destroy_infra.py
```

`bootstrap.py` defaults to `full`. It runs the remote-state backend, bootstrap infrastructure, Ansible configuration, and operator-credential steps in order. The destroy script only handles the bootstrap layer; destroy the EKS/RDS platform through the platform-infrastructure pipeline first. Review every Terraform plan before applying it.

The operator-credentials CLI stores values in AWS Secrets Manager. `--show` prints credential names and non-secret usernames but never prints password values. `--only` can update selected fields on an existing valid secret; targeted creation of a new secret must still supply all required credentials, and the optional read-only database username/password are validated as a pair.

The additive operator IP allowlist is stored in the Terraform state bucket. Removing an entry or deleting the list affects access controls; review the list before pruning or deleting it.

## 3. Local development commands

Each command below is independently runnable and does not provision AWS infrastructure.

| Script | Purpose |
|---|---|
| `scripts/python/local/run_local.py` | Backend/frontend test and build workflow; supports `--backend-only`, `--frontend-only`, `--frontend-coverage`, `--skip-it`, and `--clear-cache`. |
| `scripts/python/local/mvn.py` | Target Maven operations; `--list-services` can be used without an action. |
| `scripts/python/local/npm.py` | Targeted frontend npm operations. |
| `scripts/python/local/gitleaks.py` | Run Gitleaks locally in Docker. |
| `scripts/python/local/trivy_local.py` | Trivy filesystem/config/image scans; `--build` is explicit and only affects image scans. |
| `scripts/python/local/sonarqube_local.py` | Local SonarQube analysis; optional managed local server. |
| `scripts/python/local/compose.py` | Start/stop/log/build/configure the local Docker Compose stack. |
| `scripts/python/local/smoke_test.py` | Check application endpoints and the seed-admin login through the gateway. |
| `scripts/python/local/docker_clean.py` | Clean local Compose resources; volume/cache pruning is opt-in. |
| `scripts/python/local/generate_jwt_keys.py` | Generate local RSA key pairs and update `.env`; optional `--kubernetes [namespace]`. |
| `scripts/python/local/devsecops_check.py` | Run the complete local gate, with opt-out flags for optional stages. |

Examples:

```bash
python3 scripts/python/local/mvn.py --list-services
python3 scripts/python/local/mvn.py clean-verify --all
python3 scripts/python/local/compose.py config
python3 scripts/python/local/trivy_local.py fs
python3 scripts/python/local/trivy_local.py images --build
python3 scripts/python/local/devsecops_check.py --start-sonarqube --shutdown
python3 scripts/python/local/generate_jwt_keys.py
python3 scripts/python/local/generate_jwt_keys.py --kubernetes catalogix --force
```

Rotating JWT keys invalidates tokens issued with the previous keys. The key-generation script now asks for explicit confirmation when it detects existing values in `.env`; `--force` is for deliberate scripted rotation only.

`--skip-trivy` in the full gate disables vulnerability scanning, not image building required by the smoke-test step. Each local command is still usable by itself when you only need one check.

## 4. CI checks and tests

```bash
python3 -m unittest discover -s scripts/python/tests -v
cd scripts/python && ruff check --select E,F,W,S .
python3 scripts/python/ci/check_helm_image_tags.py infra/helm/catalogix-hc/values-dev.yaml
```

The Helm image-tag contract check is intentionally retained as Python. It catches a distinct failure mode: Helm accepts unknown `--set` keys, so the pipeline could otherwise appear successful while a service falls back to the global image tag. It needs Helm and Python’s standard library only, so it also works on a clean Jenkins agent.

The unit suite covers allowlist and credential behaviour, plus regression tests for command retry policy, Vault recovery, and local CLI behaviour. Changes to script paths should update this README and references in Terraform/Ansible documentation and Jenkinsfiles in the same change.
