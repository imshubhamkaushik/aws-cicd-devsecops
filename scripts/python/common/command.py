"""Safe subprocess runner shared by the AWS/operator scripts.

Commands are executed as argument vectors with ``shell=False``. Retry is opt-in by evidence:
only failures matching a known transient stderr pattern are retried. Unclassified errors fail
immediately, which is especially important for mutating Terraform and Ansible commands.
"""
from __future__ import annotations

import os
import shlex
import subprocess
import sys
import time
from collections.abc import Sequence
from typing import NoReturn

Command = str | Sequence[str]


def info(msg: str) -> None:
    print(f"[INFO] {msg}")


def warn(msg: str) -> None:
    print(f"[WARN] {msg}")


def error(msg: str) -> NoReturn:
    print(f"[ERROR] {msg}", file=sys.stderr)
    raise SystemExit(1)


_TRANSIENT_PATTERNS = {
    "unable to list objects in s3 bucket": "S3 backend unreachable. Check internet or AWS region.",
    "connection reset by peer": "Network instability detected. Please retry.",
}

_NON_TRANSIENT_PATTERNS = {
    "unable to locate credentials": "AWS credentials not configured. Run: aws configure",
    "accessdenied": "AWS access denied. Check IAM permissions or credentials.",
    "error: invalid": "Terraform configuration error. Check your .tf files.",
    "decryption failed": "Invalid Ansible Vault password.",
}


def _classify_error(stderr: str | None) -> tuple[str | None, bool | None]:
    """Return (friendly message, transient?) for recognised stderr text."""
    if not stderr:
        return None, None

    lowered = stderr.lower()
    for pattern, message in _NON_TRANSIENT_PATTERNS.items():
        if pattern in lowered:
            return message, False
    for pattern, message in _TRANSIENT_PATTERNS.items():
        if pattern in lowered:
            return message, True
    return None, None


def _normalise_command(cmd: Command) -> list[str]:
    """Convert legacy command strings to argv without invoking a shell."""
    parts = shlex.split(cmd) if isinstance(cmd, str) else [str(part) for part in cmd]
    if not parts:
        raise ValueError("Command cannot be empty")
    return parts


def _execute_once(
    cmd: list[str],
    cwd,
    capture_output: bool,
    merged_env: dict[str, str],
    check: bool,
):
    """Run once; stream output by default, or capture it for parsing/error classification."""
    result = subprocess.run(
        cmd,
        cwd=cwd,
        shell=False,
        text=True,
        capture_output=capture_output,
        env=merged_env,
        check=False,
    )
    if check and result.returncode != 0:
        raise subprocess.CalledProcessError(
            result.returncode,
            cmd,
            output=result.stdout if capture_output else None,
            stderr=result.stderr if capture_output else None,
        )
    return result.stdout if capture_output and check else result.returncode if not check else None


def _handle_failure(
    exc: subprocess.CalledProcessError,
    cmd: list[str],
    attempt: int,
    retries: int,
    delay: float,
    capture_output: bool,
) -> None:
    """Retry only explicitly recognised transient failures; all others fail immediately."""
    stderr = (exc.stderr or "") if capture_output else ""
    display_cmd = shlex.join(cmd)
    custom_msg, is_transient = _classify_error(stderr)

    warn(f"Command failed (attempt {attempt + 1}/{retries}): {display_cmd}")
    if stderr.strip():
        print(stderr.rstrip())

    if is_transient is not True:
        # Unknown is not the same as transient. Never blindly repeat an operation that may
        # already have made state changes (e.g. terraform apply or ansible-playbook).
        error(custom_msg or f"Command failed with exit code {exc.returncode}: {display_cmd}")

    if attempt + 1 >= retries:
        error(custom_msg or f"Command failed after {retries} attempts: {display_cmd}")

    time.sleep(delay * (attempt + 1))


def run_command(
    cmd: Command,
    cwd=None,
    capture_output: bool = False,
    retries: int = 3,
    delay: float = 5,
    env: dict[str, str] | None = None,
    check: bool = True,
):
    """Run a command without a shell.

    ``check=True`` returns captured stdout or ``None`` and may retry only explicitly classified
    transient failures. ``check=False`` runs exactly once and returns the exit status (needed for
    commands such as ``terraform plan -detailed-exitcode``).
    """
    parts = _normalise_command(cmd)
    merged_env = {**os.environ, **(env or {})}

    if not check:
        return _execute_once(parts, cwd, capture_output, merged_env, check=False)
    if retries < 1:
        raise ValueError("retries must be at least 1")

    for attempt in range(retries):
        try:
            return _execute_once(parts, cwd, capture_output, merged_env, check=True)
        except subprocess.CalledProcessError as exc:
            _handle_failure(exc, parts, attempt, retries, delay, capture_output)

    # _handle_failure exits for every failed attempt; this protects the type contract if that
    # helper is changed later.
    error(f"Command failed: {shlex.join(parts)}")
