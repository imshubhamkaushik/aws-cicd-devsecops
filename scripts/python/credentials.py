#!/usr/bin/env python3

"""
Manage the operator-selected Catalogix credentials in AWS Secrets Manager.

The script owns one Secrets Manager secret per environment:

    <cluster-name>/operator-credentials

Required credentials:
    db_master_password
    rabbitmq_user / rabbitmq_password
    seed_admin_password
    grafana_admin_user / grafana_admin_password

Optional credentials:
    db_readonly_username / db_readonly_password

The same password rules are used by the validation and pipeline checks.
The standard library is used so the script runs on a developer machine and
on the Jenkins host without additional Python dependencies.
"""

import argparse
import getpass
import json
import os
import re
import secrets
import string
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from typing import Any

CLUSTER_NAMES = {
    "dev": "catalogix-cluster-dev",
    "staging": "catalogix-cluster-staging",
}

SECRET_SUFFIX = "operator-credentials"  # NOSONAR - this is a secret name, not a credential
MIN_LENGTH = 8
MAX_LENGTH = 64
FORBIDDEN_CHARS = '/"@'

USERNAME_RE = re.compile(r"^[a-z][a-z\d_]{2,31}$")
GRAFANA_USER_RE = re.compile(r"^[A-Za-z\d_.-]{3,32}$")


@dataclass(frozen=True)
class Credential:
    """Describe one operator-managed credential."""

    key: str
    description: str
    kind: str = "password"
    required: bool = True
    default: str | None = None

    @property
    def is_secret(self) -> bool:
        return self.kind == "password"


CREDENTIALS = [
    Credential("db_master_password", "RDS PostgreSQL master password (user 'catalogix')"),
    Credential("rabbitmq_user", "RabbitMQ username", kind="username", default="catalogix"),
    Credential("rabbitmq_password", "RabbitMQ password"),
    Credential(
        "seed_admin_password",
        r"Password of the first app admin (admin@catalogix.local)",
    ),
    Credential(
        "grafana_admin_user",
        "Grafana admin username",
        kind="grafana_user",
        default="admin",
    ),
    Credential("grafana_admin_password", "Grafana admin password"),
    Credential(
        "db_readonly_username",
        "OPTIONAL read-only database login — username",
        kind="username",
        required=False,
        default="catalogix_readonly",
    ),
    Credential(
        "db_readonly_password",
        "OPTIONAL read-only database login — password (leave blank to skip)",
        required=False,
    ),
]


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------

def password_problems(password: Any) -> list[str]:
    """Return human-readable password-policy violations."""

    if not isinstance(password, str):
        return ["must be text"]

    problems: list[str] = []

    if not MIN_LENGTH <= len(password) <= MAX_LENGTH:
        problems.append(f"must be {MIN_LENGTH}-{MAX_LENGTH} characters long")
    if not re.search(r"[a-z]", password):
        problems.append("needs at least one lowercase letter")
    if not re.search(r"[A-Z]", password):
        problems.append("needs at least one uppercase letter")
    if not re.search(r"\d", password):
        problems.append("needs at least one digit")
    if any(char in password for char in FORBIDDEN_CHARS) or re.search(r"\s", password):
        problems.append('must not contain / " @ or whitespace')
    if not all(32 < ord(char) < 127 for char in password):
        problems.append("must use printable ASCII characters only")

    return problems


def username_problems(kind: str, value: Any) -> list[str]:
    """Return validation errors for username-style credentials."""

    pattern = GRAFANA_USER_RE if kind == "grafana_user" else USERNAME_RE
    if pattern.fullmatch(value or ""):
        return []

    if kind == "grafana_user":
        return ["3-32 characters: letters, digits, underscore, dot or hyphen"]

    return [
        "3-32 characters, start with a lowercase letter, "
        "then lowercase letters, digits or underscore"
    ]


def value_problems(credential: Credential, value: Any) -> list[str]:
    """Validate a credential according to its declared kind."""

    if credential.is_secret:
        return password_problems(value)
    return username_problems(credential.kind, value)


def generate_password(length: int = 20) -> str:
    """Generate a password that satisfies the local password policy."""

    alphabet = string.ascii_letters + string.digits

    while True:
        candidate = "".join(secrets.choice(alphabet) for _ in range(length))
        if not password_problems(candidate):
            return candidate


# ---------------------------------------------------------------------------
# AWS access
# ---------------------------------------------------------------------------

class SecretNotFound(Exception):
    """Raised when the environment secret does not exist."""


def secret_id(env: str) -> str:
    """Return the Secrets Manager identifier for an environment."""

    return f"{CLUSTER_NAMES[env]}/{SECRET_SUFFIX}"


def _aws(args: list[str], region: str | None):
    """Run an AWS Secrets Manager CLI command."""

    command = ["aws", "secretsmanager", *args]
    if region:
        command += ["--region", region]

    return subprocess.run(command, capture_output=True, text=True)


def fetch_secret(env: str, region: str) -> dict[str, Any]:
    """Return the current secret or raise SecretNotFound."""

    result = _aws(
        [
            "get-secret-value",
            "--secret-id",
            secret_id(env),
            "--query",
            "SecretString",
            "--output",
            "text",
        ],
        region,
    )

    if result.returncode != 0:
        if "ResourceNotFoundException" in result.stderr:
            raise SecretNotFound(secret_id(env))
        raise RuntimeError(
            f"aws secretsmanager get-secret-value failed: {result.stderr.strip()}"
        )

    return json.loads(result.stdout)


def store_secret(
    env: str,
    region: str,
    values: dict[str, Any],
    exists: bool,
) -> None:
    """Write the JSON secret through a 0600 temporary file.

    Using file:// keeps credential values out of the AWS CLI process arguments.
    """

    fd, path = tempfile.mkstemp(prefix="catalogix-cred-", suffix=".json")

    try:
        os.chmod(path, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(values, handle)

        if exists:
            args = [
                "put-secret-value",
                "--secret-id",
                secret_id(env),
                "--secret-string",
                f"file://{path}",
            ]
        else:
            args = [
                "create-secret",
                "--name",
                secret_id(env),
                "--secret-string",
                f"file://{path}",
                "--description",
                "Operator-chosen credentials for Catalogix "
                "(scripts/python/credentials.py)",
            ]

        result = _aws(args, region)
        if result.returncode != 0:
            raise RuntimeError(
                f"aws secretsmanager write failed: {result.stderr.strip()}"
            )
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


# ---------------------------------------------------------------------------
# Check / show
# ---------------------------------------------------------------------------

def check_values(values: dict[str, Any]) -> list[str]:
    """Return problems with a stored secret."""

    problems: list[str] = []

    for credential in CREDENTIALS:
        value = values.get(credential.key)

        if value in (None, ""):
            if credential.required:
                problems.append(f"{credential.key}: missing")
            continue

        for problem in value_problems(credential, value):
            problems.append(f"{credential.key}: {problem}")

    if values.get("db_readonly_password") and not values.get("db_readonly_username"):
        problems.append(
            "db_readonly_username: required when db_readonly_password is set"
        )

    return problems


def _print_problems(problems: list[str]) -> None:
    for problem in problems:
        print(f"  - {problem}")


def cmd_check(env: str, region: str) -> int:
    try:
        values = fetch_secret(env, region)
    except SecretNotFound:
        print(
            f"ERROR: {secret_id(env)} does not exist. "
            f"Run: python scripts/python/credentials.py --env {env}"
        )
        return 1

    problems = check_values(values)
    if problems:
        print(f"ERROR: {secret_id(env)} is incomplete or invalid:")
        _print_problems(problems)
        print(
            f"Fix with: python scripts/python/credentials.py --env {env}"
        )
        return 1

    print(
        f"OK: {secret_id(env)} has every required credential "
        "and they meet the password policy."
    )
    return 0


def cmd_show(env: str, region: str) -> int:
    try:
        values = fetch_secret(env, region)
    except SecretNotFound:
        print(
            f"{secret_id(env)} does not exist yet. "
            "Run without --show to create it."
        )
        return 1

    print(secret_id(env))

    for credential in CREDENTIALS:
        value = values.get(credential.key)

        if value in (None, ""):
            state = "MISSING" if credential.required else "not set (optional)"
        elif credential.is_secret:
            state = "set (hidden)"
        else:
            state = f"set: {value}"

        print(f"  {credential.key:24s} {state}")

    return 0


# ---------------------------------------------------------------------------
# Interactive credential editing
# ---------------------------------------------------------------------------

def _prompt_secret(credential: Credential, have: bool) -> str | None:
    hint = " — Enter keeps the current value" if have else ""
    if not credential.required:
        hint += " — Enter to skip"

    while True:
        value = getpass.getpass(
            f"  {credential.description}{hint}\n"
            "  (type g to generate one) > "
        )

        if value == "":
            if have or not credential.required:
                return None
            print("    This one is required.")
            continue

        if value.lower() == "g":
            generated = generate_password()
            print(
                "    Generated: "
                f"{generated} <- shown once; it is stored in Secrets Manager"
            )
            return generated

        problems = password_problems(value)
        if problems:
            print("    Not accepted: " + "; ".join(problems))
            continue

        confirmation = getpass.getpass("  Repeat it > ")
        if confirmation != value:
            print("    The two entries differ — try again.")
            continue

        return value


def _prompt_text(credential: Credential, current: Any) -> str:
    default: str = (
        current
        if isinstance(current, str) and current
        else credential.default or ""
    )

    while True:
        value: str = input(
            f"  {credential.description} [{default}] > "
        ).strip() or default

        problems = username_problems(credential.kind, value)
        if not problems:
            return value

        print("    Not accepted: " + "; ".join(problems))


def _load_secret(env: str, region: str) -> tuple[dict[str, Any], bool]:
    try:
        return fetch_secret(env, region), True
    except SecretNotFound:
        return {}, False


def _select_credentials(
    only: list[str] | None,
) -> tuple[list[Credential], int | None]:
    wanted = [
        credential
        for credential in CREDENTIALS
        if only is None or credential.key in only
    ]

    if only is None or len(wanted) == len(set(only)):
        return wanted, None

    unknown = set(only) - {credential.key for credential in CREDENTIALS}
    print(f"ERROR: unknown key(s): {', '.join(sorted(unknown))}")
    return [], 1


def _should_skip_readonly(
    credential: Credential,
    only: list[str] | None,
    exists: bool,
    prompted: bool,
) -> tuple[bool, bool]:
    """Ask once whether a new secret should include read-only DB credentials."""

    is_readonly = credential.key.startswith("db_readonly")
    if not is_readonly or only is not None or exists or prompted:
        return False, prompted

    answer = input(
        "Create a read-only database login for looking at data "
        "with psql/pgAdmin? (y/N) "
    ).strip().lower()

    return answer not in ("y", "yes"), True


def _prompt_credential(
    credential: Credential,
    values: dict[str, Any],
) -> str | None:
    if credential.is_secret:
        return _prompt_secret(
            credential,
            have=bool(values.get(credential.key)),
        )
    return _prompt_text(credential, values.get(credential.key))


def _collect_changes(
    wanted: list[Credential],
    values: dict[str, Any],
    exists: bool,
    only: list[str] | None,
) -> dict[str, str]:
    changed: dict[str, str] = {}
    skip_readonly = False
    prompted_readonly = False

    for credential in wanted:
        skip_readonly, prompted_readonly = _should_skip_readonly(
            credential,
            only,
            exists,
            prompted_readonly,
        )

        if credential.key.startswith("db_readonly") and skip_readonly:
            continue

        new_value = _prompt_credential(credential, values)
        if new_value is not None and new_value != values.get(credential.key):
            changed[credential.key] = new_value

    return changed


def _validate_changes(
    values: dict[str, Any],
    changed: dict[str, str],
    only: list[str] | None,
) -> list[str]:
    if only is not None:
        return []

    return check_values({**values, **changed})


def _save_changes(
    env: str,
    region: str,
    values: dict[str, Any],
    changed: dict[str, str],
    exists: bool,
) -> None:
    merged = {**values, **changed}
    store_secret(env, region, merged, exists)

    names = ", ".join(sorted(changed))
    print(
        f"\nSaved {len(changed)} value(s) to {secret_id(env)}: {names}"
    )
    print(
        "Next: run the platform-infra pipeline "
        "(Terraform reads db_master_password from here)."
    )

    if "seed_admin_password" in changed and exists:
        print(
            "Note: an admin that already exists keeps its current password — "
            "change it on the Account page."
        )


def interactive(
    env: str,
    region: str,
    only: list[str] | None = None,
) -> int:
    values, exists = _load_secret(env, region)

    print(f"\nCredentials for '{env}' -> {secret_id(env)}")
    print(
        f"Password rule: {MIN_LENGTH}-{MAX_LENGTH} chars, "
        "upper + lower + digit, no / \" @ or spaces "
        "(12+ recommended).\n"
    )

    wanted, error_code = _select_credentials(only)
    if error_code is not None:
        return error_code

    changed = _collect_changes(wanted, values, exists, only)

    if not changed:
        print("\nNothing changed.")
        return cmd_check(env, region) if exists else 1

    problems = _validate_changes(values, changed, only)
    if problems:
        print("\nNot saved — still incomplete:")
        _print_problems(problems)
        return 1

    _save_changes(env, region, values, changed, exists)
    return 0


# ---------------------------------------------------------------------------
# Entry point helpers
# ---------------------------------------------------------------------------

def resolve_region(cli_region: str | None) -> str | None:
    if cli_region:
        return cli_region

    for variable in ("AWS_REGION", "AWS_DEFAULT_REGION"):
        region = os.environ.get(variable)
        if region:
            return region

    result = subprocess.run(
        ["aws", "configure", "get", "region"],
        capture_output=True,
        text=True,
        check=False,
    )
    return result.stdout.strip() or None


def cmd_delete(
    env: str,
    region: str,
    assume_yes: bool,
) -> int:
    """Permanently delete the operator-credentials secret."""

    sid = secret_id(env)

    try:
        fetch_secret(env, region)
    except SecretNotFound:
        print(f"{sid} does not exist — nothing to delete.")
        return 0

    if not assume_yes:
        answer = input(
            f"Permanently delete {sid}? This cannot be undone. "
            "Type 'yes' to confirm: "
        ).strip()

        if answer != "yes":
            print("Not deleted.")
            return 1

    result = _aws(
        [
            "delete-secret",
            "--secret-id",
            sid,
            "--force-delete-without-recovery",
        ],
        region,
    )

    if result.returncode != 0:
        print(f"ERROR: could not delete {sid}: {result.stderr.strip()}")
        return 1

    print(f"Deleted {sid}.")
    return 0


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Set the operator-chosen credentials "
            "for a Catalogix environment."
        )
    )
    parser.add_argument(
        "--env",
        choices=sorted(CLUSTER_NAMES),
        required=True,
    )
    parser.add_argument(
        "--region",
        help="AWS region (default: AWS_REGION, then aws configure)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate only; exit 1 if anything is missing or invalid",
    )
    parser.add_argument(
        "--show",
        action="store_true",
        help="list which keys are set (never prints passwords)",
    )
    parser.add_argument(
        "--only",
        nargs="+",
        metavar="KEY",
        help="change only these keys",
    )
    parser.add_argument(
        "--delete",
        action="store_true",
        help="permanently delete this environment's credentials secret",
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="with --delete, skip the confirmation prompt",
    )
    return parser


def _dispatch_command(args: argparse.Namespace, region: str) -> int:
    if args.delete:
        return cmd_delete(args.env, region, args.yes)
    if args.check:
        return cmd_check(args.env, region)
    if args.show:
        return cmd_show(args.env, region)
    return interactive(args.env, region, only=args.only)


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    region = resolve_region(args.region)

    if not region:
        print("ERROR: no AWS region — pass --region or set AWS_REGION.")
        return 2

    return _dispatch_command(args, region)


if __name__ == "__main__":
    sys.exit(main())
