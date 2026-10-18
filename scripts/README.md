# Scripts

Everything an operator runs from a laptop or a Linux VM: installing tools, bootstrapping AWS, managing credentials and the IP allowlist, and the local checks that mirror the CI pipelines. All commands are run **from the repository root**. The shell scripts are only used where Python cannot yet exist; everything else is Python using the standard library (plus `boto3` and Ansible inside `.venv`).

```text
scripts/
  generate-local-jwt-keys.sh   RSA keys for Compose and local Kubernetes
  bash/
    install-all.sh             bare Linux machine -> tools installed
    install-python.sh          makes sure Python 3 exists
    create-key-pair.sh         SSH key for the Jenkins and SonarQube servers
  ci/
    check-helm-image-tags.sh   contract test used by Jenkinsfile.lint
  python/
    install_tools.py           installs AWS CLI, Terraform, Ansible, jq, ssh-keygen
    bootstrap.py               the AWS bootstrap CLI (below)
    backend_bootstrap.py, bootstrap_infra.py, ansible_runner.py, credentials.py, allowlist.py, destroy_infra.py
    local_scans/               local build, scan and smoke-test helpers
    utils/                     command runner and .venv re-exec
    tests/                     unit tests for allowlist.py and credentials.py
    pyproject.toml             ruff settings (E, F, W, S rules)
```

## Setting up a machine

```bash
bash scripts/bash/install-all.sh              # Python if missing, then every tool
bash scripts/bash/install-all.sh --only terraform,aws     # extra arguments go to install_tools.py
```

`install_tools.py` installs the AWS CLI v2 (official installer), Terraform (official zip, checked against its published SHA-256; `--terraform-version` pins a version), Ansible and boto3 into a virtualenv at `.venv`, the Galaxy collections from `infra/ansible/requirements.yaml`, `jq` and `ssh-keygen`. It works on apt, dnf, yum and zypper systems; on Windows use WSL. Every other Python script re-executes itself inside `.venv` automatically (`utils/venv.py`), so there is nothing to activate. Docker, Maven, Node, kubectl and Helm are **not** installed by this script.

Then configure AWS (`aws configure`) and create the SSH key once:

```bash
bash scripts/bash/create-key-pair.sh          # ed25519 key in ~/.ssh/catalogix-key(.pub), no passphrase, never in the repository
```

## Bootstrapping AWS (`bootstrap.py`)

```bash
python3 scripts/python/bootstrap.py [backend|infra|ansible|credentials|full]     # default: full
```

It checks the required tools and that your AWS credentials work, then runs one step, or all of them in this order for `full`:

| Step | What it does |
|---|---|
| `backend` | Terraform for `infra/terraform/backend-bootstrap`: the S3 state bucket. Shows the plan and asks before applying; skips the apply when nothing changed |
| `infra` | Adds your current IP to the allowlist (`allowlist.py sync`), then Terraform for `bootstrap-infra` (VPC, Jenkins and SonarQube servers, ...), then waits until the instances answer |
| `ansible` | Configures both servers: see [../infra/ansible/README.md](../infra/ansible/README.md) |
| `credentials` | Prompts for the environment, then `credentials.py` stores the passwords you choose in AWS Secrets Manager |

`full` asks for confirmation before the infrastructure step and finishes by telling you to run the `platform-infra` pipeline in Jenkins. Every step is safe to repeat.

### Operator credentials (`credentials.py`)

Stores one Secrets Manager secret per environment, `<cluster>/operator-credentials`: `db_master_password`, `rabbitmq_user`, `rabbitmq_password`, `seed_admin_password`, `grafana_admin_user`, `grafana_admin_password` and optionally a read-only database user. The password rules are the same ones the pipelines check. Passwords travel through a mode-600 temporary file, never the command line, and are never printed.

```bash
python3 scripts/python/credentials.py --env dev            # prompt for everything and save
python3 scripts/python/credentials.py --env dev --check    # validate only, exit 1 if anything is missing or invalid
python3 scripts/python/credentials.py --env dev --show     # which keys are set, never the values
python3 scripts/python/credentials.py --env dev --only seed_admin_password    # change selected keys
python3 scripts/python/credentials.py --env dev --delete --yes                # delete the secret (the platform-infra Destroy does this too)
```

Only `dev` has this flow wired into the pipelines; see [../infra/README.md](../infra/README.md).

### IP allowlist (`allowlist.py`)

The Jenkins security group (SSH 22, UI 8080) and the EKS API accept only addresses on a list kept in the state bucket (`access/allowed-cidrs.json`), so every machine you work from sees the same list. Terraform reads it through a generated variable file.

```bash
python3 scripts/python/allowlist.py sync                  # add my current IP if new; never removes anything
python3 scripts/python/allowlist.py list
python3 scripts/python/allowlist.py add 203.0.113.7/32 --label office
python3 scripts/python/allowlist.py remove 203.0.113.7/32 [--force]     # --force lets you remove the IP you are on
python3 scripts/python/allowlist.py prune --older-than 30 [--all-but-current] [--yes]
python3 scripts/python/allowlist.py delete                # whole list, used by destroy_infra.py
```

After changing the list, re-run Terraform (`bootstrap.py infra`, or the `platform-infra` pipeline for the EKS API) so the security groups pick it up.

### Destroying the bootstrap infrastructure (`destroy_infra.py`)

```bash
python3 scripts/python/destroy_infra.py
```

Plans and shows a destroy of `bootstrap-infra`, asks for `yes`, applies it, then deletes the IP allowlist. It does **not** destroy the platform (EKS, RDS: use the pipeline's `Destroy` action first) and does **not** delete the state bucket from `backend-bootstrap`.

## Local application keys (`generate-local-jwt-keys.sh`)

```bash
scripts/generate-local-jwt-keys.sh                          # write both key pairs into .env
scripts/generate-local-jwt-keys.sh --kubernetes [namespace] # also create the catalogix-jwt-keys Secret (default namespace catalogix)
```

Creates the user-token pair (3072-bit; only user-svc gets the private half) and the one internal service-to-service pair (2048-bit) shared by every service, and writes them to `.env` (copied from `.env.example` if it does not exist). **Re-running replaces both pairs**, so every token already issued stops working. `.env` is git-ignored; keep it that way.

## Local checks (`python/local_scans/`)

These run the same tools as the pipelines so problems show up before a push. Tool versions come from `infra/ansible/group_vars/all/vars.yaml`. Docker must be running for most of them.

| Script | Purpose |
|---|---|
| `devsecops_check.py` | The whole gate in order: Gitleaks, `mvn clean verify`, frontend install/test/build, SonarQube, Trivy, Compose build and smoke test. Flags: `--clear-cache`, `--start-sonarqube`, `--skip-sonar`, `--skip-trivy`, `--skip-gitleaks`, `--skip-smoke`, `--shutdown` |
| `run_local.py` | Tests and builds only. `--backend-only`, `--frontend-only`, `--frontend-coverage`, `--skip-it`, `--clear-cache` |
| `mvn.py`, `npm.py` | Targeted Maven or npm operations |
| `gitleaks.py` | Gitleaks in Docker. Scans the full history when `.git` exists, otherwise the working tree |
| `trivy_local.py` | `fs`, `config`, `images` or `all` targets; `--build` first builds the images |
| `sonarqube_local.py` | SonarQube analysis; `--start-server` starts a local server on port 9000 and creates its token |
| `compose.py` | `start`, `stop`, `restart`, `status`, `logs`, `build`, `down`, `pull`, `config` for the Compose stack, optionally per `--service` |
| `smoke_test.py` | Checks through the gateway (`http://localhost:11000` by default), including a login as the seed admin. Works for Compose and local Kubernetes |
| `docker_clean.py` | Brings the Compose stack down (with orphans); `--images`, `--volumes` (deletes local data, needs `--yes`) and `--builder-cache` are opt-in. No blanket `docker system prune` |

Example: `python3 scripts/python/local_scans/devsecops_check.py --start-sonarqube --shutdown`.

## Tests and linting

```bash
python3 -m unittest discover -s scripts/python/tests     # 59 tests, no AWS access needed
cd scripts/python && ruff check --select E,F,W,S .       # needs ruff installed
bash scripts/ci/check-helm-image-tags.sh                 # needs Helm and PyYAML
```

The unit tests cover the password rules and Secrets Manager handling in `credentials.py` and the CIDR logic in `allowlist.py`. The bootstrap scripts that run Terraform and Ansible have no automated tests.
