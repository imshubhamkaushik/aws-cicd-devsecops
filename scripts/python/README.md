# Python script directory

This directory contains all Catalogix Python tooling. Entry points are separated by target:

- `setup/`: workstation setup helpers, such as generating the EC2 SSH key pair.
- `local/`: independent local build, test, scan, Compose, smoke-test, and local JWT-key commands.
- `aws/`: AWS bootstrap, Terraform orchestration, Ansible execution, credentials, and IP allowlist operations.
- `ci/`: Python-based contract checks invoked from CI.
- `common/`: shared command execution and virtual-environment helpers.
- `tests/`: offline unit tests for operator tooling.

`install_tools.py` stays at this directory's root because it is the standard-library-only bootstrap entry point. It creates `.venv` and installs Ansible, boto3, and botocore there; the operator scripts use that environment automatically when available.

Start with [`../README.md`](../README.md) for supported commands, prerequisites, and usage examples. Run paths from the repository root, for example:

```bash
python3 scripts/python/aws/bootstrap.py --help
python3 scripts/python/local/mvn.py --list-services
python3 -m unittest discover -s scripts/python/tests -v
```
