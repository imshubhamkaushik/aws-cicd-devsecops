#!/usr/bin/env python3

"""
Manage operator-selected Catalogix credentials in categorized AWS Secrets Manager secrets.

Secrets (AWS Secrets Manager uses a name prefix, not real folders):
    <cluster-name>/credentials/database       RDS master + optional read-only DB login
    <cluster-name>/credentials/application    RabbitMQ login + seed admin password
    <cluster-name>/credentials/observability  Grafana admin login

The legacy <cluster-name>/operator-credentials secret can be migrated with --migrate-legacy.
It is deliberately retained after migration until the operator verifies the new secrets and
removes the old one. Terraform reads only the database category; ESO reads application and
observability categories directly, keeping those human/application passwords out of Terraform state.

The same password rules are used by the validation and pipeline checks.
The standard library is used so the script runs on a developer machine and
on the Jenkins host without additional Python dependencies.
"""

from __future__ import annotations

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
from pathlib import Path
from typing import Any

# Support direct execution (python scripts/python/aws/credentials.py).
PYTHON_ROOT = Path(__file__).resolve().parents[1]
if str(PYTHON_ROOT) not in sys.path:
    sys.path.insert(0, str(PYTHON_ROOT))

from common.venv import reexec_in_venv

if __name__ == "__main__":
    reexec_in_venv()

CLUSTER_NAMES = {
    "dev": "catalogix-cluster-dev",
    "staging": "catalogix-cluster-staging",
}

LEGACY_SECRET_SUFFIX = "operator-credentials"  # NOSONAR - legacy secret name, not a credential
CATEGORY_SUFFIXES = {
    "database": "credentials/database",
    "application": "credentials/application",
    "observability": "credentials/observability",
}
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

CREDENTIALS_BY_CATEGORY = {
    "database": ["db_master_password", "db_readonly_username", "db_readonly_password"],
    "application": ["rabbitmq_user", "rabbitmq_password", "seed_admin_password"],
    "observability": ["grafana_admin_user", "grafana_admin_password"],
}
CATEGORY_BY_KEY = {
    key: category
    for category, keys in CREDENTIALS_BY_CATEGORY.items()
    for key in keys
}


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

class SecretNotFound(Exception):
    """Raised when a requested Secrets Manager secret does not exist."""


def legacy_secret_id(env: str) -> str:
    """Return the pre-categorisation secret name used by older repository versions."""
    return f"{CLUSTER_NAMES[env]}/{LEGACY_SECRET_SUFFIX}"


def secret_id(env: str, category: str | None = None) -> str:
    """Return a category secret name; with no category, return the legacy name."""
    if category is None:
        return legacy_secret_id(env)
    if category not in CATEGORY_SUFFIXES:
        raise ValueError(f"Unknown credential category: {category}")
    return f"{CLUSTER_NAMES[env]}/{CATEGORY_SUFFIXES[category]}"


def _aws(args: list[str], region: str | None):
    """Run an AWS Secrets Manager CLI command."""
    command = ["aws", "secretsmanager", *args]
    if region:
        command += ["--region", region]
    return subprocess.run(command, capture_output=True, text=True)


def _fetch_secret_id(sid: str, region: str) -> dict[str, Any]:
    result = _aws(
        ["get-secret-value", "--secret-id", sid, "--query", "SecretString", "--output", "text"],
        region,
    )
    if result.returncode != 0:
        if "ResourceNotFoundException" in result.stderr or "Secrets Manager can't find the specified secret" in result.stderr:
            raise SecretNotFound(sid)
        raise RuntimeError(f"aws secretsmanager get-secret-value failed for {sid}: {result.stderr.strip()}")
    try:
        value = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Secret {sid} does not contain valid JSON.") from exc
    if not isinstance(value, dict):
        raise RuntimeError(f"Secret {sid} must contain a JSON object.")
    return value


def fetch_category_secret(env: str, region: str, category: str) -> dict[str, Any]:
    """Read one category secret."""
    return _fetch_secret_id(secret_id(env, category), region)


def fetch_legacy_secret(env: str, region: str) -> dict[str, Any]:
    """Read the legacy combined secret."""
    return _fetch_secret_id(legacy_secret_id(env), region)


def load_credential_state(
    env: str, region: str
) -> tuple[dict[str, Any], set[str], bool]:
    """Return merged values, present categories, and whether the legacy secret exists.

    New category secrets take precedence over legacy fields. Legacy values are only a migration
    fallback; --check still requires all new category secrets to exist.
    """
    values: dict[str, Any] = {}
    present_categories: set[str] = set()
    for category in CATEGORY_SUFFIXES:
        try:
            category_values = fetch_category_secret(env, region, category)
        except SecretNotFound:
            continue
        allowed = set(CREDENTIALS_BY_CATEGORY[category])
        unexpected = set(category_values) - allowed
        if unexpected:
            raise RuntimeError(
                f"Secret {secret_id(env, category)} contains keys outside the {category} category: "
                + ", ".join(sorted(unexpected))
            )
        values.update(category_values)
        present_categories.add(category)

    legacy_exists = False
    try:
        legacy_values = fetch_legacy_secret(env, region)
        legacy_exists = True
        for key, value in legacy_values.items():
            values.setdefault(key, value)
    except SecretNotFound:
        pass

    if not present_categories and not legacy_exists:
        raise SecretNotFound(secret_id(env, "database"))
    return values, present_categories, legacy_exists


def fetch_secret(env: str, region: str) -> dict[str, Any]:
    """Return the merged operator credential view (category secrets preferred over legacy)."""
    values, _, _ = load_credential_state(env, region)
    return values


def store_secret(
    env: str,
    region: str,
    category: str,
    values: dict[str, Any],
    exists: bool,
) -> None:
    """Write one category JSON through a private temporary file (mode 0600)."""
    allowed = set(CREDENTIALS_BY_CATEGORY[category])
    payload = {key: value for key, value in values.items() if key in allowed and value not in (None, "")}
    fd, path = tempfile.mkstemp(prefix="catalogix-cred-", suffix=".json")
    try:
        os.chmod(path, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)

        sid = secret_id(env, category)
        if exists:
            args = ["put-secret-value", "--secret-id", sid, "--secret-string", f"file://{path}"]
        else:
            args = [
                "create-secret", "--name", sid, "--secret-string", f"file://{path}",
                "--description", f"Catalogix {category} credentials ({env}); managed by credentials.py",
            ]
        result = _aws(args, region)
        if result.returncode != 0:
            raise RuntimeError(f"aws secretsmanager write failed for {sid}: {result.stderr.strip()}")
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def _category_exists(env: str, region: str, category: str) -> bool:
    try:
        fetch_category_secret(env, region, category)
        return True
    except SecretNotFound:
        return False


def cmd_migrate_legacy(env: str, region: str) -> int:
    """Split a valid legacy combined secret into category secrets without deleting the source."""
    try:
        legacy = fetch_legacy_secret(env, region)
    except SecretNotFound:
        print(f"Legacy secret {legacy_secret_id(env)} does not exist; nothing to migrate.")
        return 0 if all(_category_exists(env, region, c) for c in CATEGORY_SUFFIXES) else 1

    problems = check_values(legacy)
    if problems:
        print(f"ERROR: cannot migrate invalid legacy secret {legacy_secret_id(env)}:")
        _print_problems(problems)
        print("Repair the legacy secret first, or run the credential editor against a backup.")
        return 1

    for category, keys in CREDENTIALS_BY_CATEGORY.items():
        existing = {}
        try:
            existing = fetch_category_secret(env, region, category)
            exists = True
        except SecretNotFound:
            exists = False
        payload = {key: legacy[key] for key in keys if key in legacy}
        # Keep any already-migrated category values rather than overwriting newer rotations.
        payload = {**payload, **existing}
        store_secret(env, region, category, payload, exists)
        print(f"Migrated {category} credentials to {secret_id(env, category)}")

    print("Migration complete. The legacy secret was retained for recovery.")
    print("Run --check, confirm the platform/app pipelines work, then remove the legacy secret manually.")
    return 0


# ---------------------------------------------------------------------------
# Check / show

def check_values(values: dict[str, Any]) -> list[str]:
    """Return problems with a merged credential view."""
    problems: list[str] = []
    for credential in CREDENTIALS:
        value = values.get(credential.key)
        if value in (None, ""):
            if credential.required:
                problems.append(f"{credential.key}: missing")
            continue
        for problem in value_problems(credential, value):
            problems.append(f"{credential.key}: {problem}")

    readonly_user = bool(values.get("db_readonly_username"))
    readonly_password = bool(values.get("db_readonly_password"))
    if readonly_password and not readonly_user:
        problems.append("db_readonly_username: required when db_readonly_password is set")
    elif readonly_user and not readonly_password:
        problems.append("db_readonly_password: required when db_readonly_username is set")
    return problems


def _print_problems(problems: list[str]) -> None:
    for problem in problems:
        print(f"  - {problem}")


def cmd_check(env: str, region: str) -> int:
    try:
        values, present_categories, legacy_exists = load_credential_state(env, region)
    except SecretNotFound:
        print(f"ERROR: categorized credential secrets for {env} do not exist. Run: python scripts/python/aws/credentials.py --env {env}")
        return 1

    missing_categories = set(CATEGORY_SUFFIXES) - present_categories
    if missing_categories:
        print("ERROR: these credential categories are missing: " + ", ".join(sorted(missing_categories)))
        if legacy_exists:
            print(f"Migrate the old combined secret with: python scripts/python/aws/credentials.py --env {env} --migrate-legacy")
        else:
            print(f"Create/fill all categories with: python scripts/python/aws/credentials.py --env {env}")
        return 1

    problems = check_values(values)
    if problems:
        print(f"ERROR: categorized credentials for '{env}' are incomplete or invalid:")
        _print_problems(problems)
        print(f"Fix with: python scripts/python/aws/credentials.py --env {env}")
        return 1

    print(f"OK: all credential categories for '{env}' exist and meet the password policy.")
    return 0


def cmd_show(env: str, region: str) -> int:
    try:
        values, present_categories, legacy_exists = load_credential_state(env, region)
    except SecretNotFound:
        print(f"No credentials found for '{env}'. Run without --show to create them.")
        return 1

    print(f"Credential categories for {env}:")
    for category in CATEGORY_SUFFIXES:
        status = "present" if category in present_categories else "MISSING"
        print(f"  {category:14s} {secret_id(env, category)} [{status}]")
    if legacy_exists:
        print(f"  legacy         {legacy_secret_id(env)} [present; remove only after successful migration verification]")

    for category, keys in CREDENTIALS_BY_CATEGORY.items():
        print(f"\n{category.title()} credentials:")
        for key in keys:
            credential = next(item for item in CREDENTIALS if item.key == key)
            value = values.get(key)
            if value in (None, ""):
                state = "MISSING" if credential.required else "not set (optional)"
            elif credential.is_secret:
                state = "set (hidden)"
            else:
                state = f"set: {value}"
            print(f"  {key:24s} {state}")
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


def _load_secret(env: str, region: str) -> tuple[dict[str, Any], set[str], bool]:
    try:
        return load_credential_state(env, region)
    except SecretNotFound:
        return {}, set(), False


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
        if not prompted_readonly:
            # Ask once; remember the answer for the remaining read-only keys.
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
    exists: bool,
) -> list[str]:
    """Validate edits without allowing a targeted update to create an invalid secret.

    Creating a secret always validates every required field. A full edit validates the complete
    merged secret. For a targeted edit to an existing secret, validate changed values plus any
    cross-field invariant for the optional read-only DB credential pair; unrelated legacy issues
    can still be repaired one field at a time.
    """
    merged = {**values, **changed}
    if not exists or only is None:
        return check_values(merged)

    problems: list[str] = []
    credentials_by_key = {credential.key: credential for credential in CREDENTIALS}
    for key, value in changed.items():
        credential = credentials_by_key.get(key)
        if credential is None:
            problems.append(f"{key}: unknown credential")
            continue
        for problem in value_problems(credential, value):
            problems.append(f"{key}: {problem}")

    readonly_keys = {"db_readonly_username", "db_readonly_password"}
    if readonly_keys.intersection(only):
        username = merged.get("db_readonly_username")
        password = merged.get("db_readonly_password")
        if bool(username) != bool(password):
            missing = "db_readonly_password" if username else "db_readonly_username"
            problems.append(f"{missing}: both read-only database credentials must be set together")
        # Validate both halves when a targeted change touches the pair, including an unchanged
        # existing half that might have been edited manually outside this CLI.
        for key in readonly_keys:
            value = merged.get(key)
            if value not in (None, ""):
                for problem in value_problems(credentials_by_key[key], value):
                    message = f"{key}: {problem}"
                    if message not in problems:
                        problems.append(message)
    return problems


def _save_changes(
    env: str,
    region: str,
    values: dict[str, Any],
    changed: dict[str, str],
    present_categories: set[str],
) -> None:
    merged = {**values, **changed}
    categories_to_write = sorted({CATEGORY_BY_KEY[key] for key in changed})
    for category in categories_to_write:
        category_payload = {key: merged[key] for key in CREDENTIALS_BY_CATEGORY[category] if key in merged}
        store_secret(
            env,
            region,
            category,
            category_payload,
            exists=category in present_categories,
        )
        present_categories.add(category)
        print(f"Saved {category} credentials to {secret_id(env, category)}")

    print(f"\nSaved {len(changed)} value(s): {', '.join(sorted(changed))}")
    print("Next: run the platform-infra pipeline. Terraform reads only the database category;")
    print("application and observability credentials are fetched directly by External Secrets Operator.")

    if "seed_admin_password" in changed and present_categories:
        print("Note: an admin that already exists keeps its current password — change it on the Account page.")


def interactive(
    env: str,
    region: str,
    only: list[str] | None = None,
) -> int:
    values, present_categories, legacy_exists = _load_secret(env, region)

    if legacy_exists and present_categories != set(CATEGORY_SUFFIXES):
        print("A legacy combined secret exists but categorized secrets are incomplete.")
        print(f"Run: python scripts/python/aws/credentials.py --env {env} --migrate-legacy")
        print("The migration preserves the old secret until you verify the new categories.")
        return 1

    print(f"\nCredentials for '{env}'")
    for category in CATEGORY_SUFFIXES:
        state = "present" if category in present_categories else "will be created"
        print(f"  {category:14s} {secret_id(env, category)} [{state}]")
    print(
        f"Password rule: {MIN_LENGTH}-{MAX_LENGTH} chars, upper + lower + digit, "
        'no / " @ or spaces (12+ recommended).\n'
    )

    wanted, error_code = _select_credentials(only)
    if error_code is not None:
        return error_code

    changed = _collect_changes(wanted, values, bool(present_categories), only)
    if not changed:
        print("\nNothing changed.")
        return cmd_check(env, region) if present_categories else 1

    problems = _validate_changes(values, changed, only, bool(present_categories))
    if problems:
        print("\nNot saved — still incomplete:")
        _print_problems(problems)
        return 1

    _save_changes(env, region, values, changed, present_categories)
    return cmd_check(env, region)


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
    """Permanently delete categorized credential secrets and the legacy secret, if present."""
    ids = [secret_id(env, category) for category in CATEGORY_SUFFIXES]
    ids.append(legacy_secret_id(env))
    existing_ids = []
    for sid in ids:
        try:
            _fetch_secret_id(sid, region)
            existing_ids.append(sid)
        except SecretNotFound:
            continue

    if not existing_ids:
        print(f"No operator credential secrets exist for {env} — nothing to delete.")
        return 0

    if not assume_yes:
        answer = input(
            "Permanently delete these Secrets Manager secrets?\n  "
            + "\n  ".join(existing_ids)
            + "\nThis cannot be undone. Type 'yes' to confirm: "
        ).strip()
        if answer != "yes":
            print("Not deleted.")
            return 1

    failures = []
    for sid in existing_ids:
        result = _aws(["delete-secret", "--secret-id", sid, "--force-delete-without-recovery"], region)
        if result.returncode != 0:
            failures.append(f"{sid}: {result.stderr.strip()}")
        else:
            print(f"Deleted {sid}.")
    if failures:
        print("ERROR: one or more secrets could not be deleted:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
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
        "--migrate-legacy",
        action="store_true",
        help="split the legacy operator-credentials secret into database/application/observability categories; keep the legacy secret",
    )
    parser.add_argument(
        "--delete",
        action="store_true",
        help="permanently delete this environment's categorized credentials and legacy secret, if present",
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
    if args.migrate_legacy:
        return cmd_migrate_legacy(args.env, region)
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
