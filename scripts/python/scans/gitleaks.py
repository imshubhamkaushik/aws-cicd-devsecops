#!/usr/bin/env python3
"""Run Gitleaks locally in Docker against the Catalogix Git repository."""

from __future__ import annotations

import argparse

from common import REPO_ROOT, require_command, run


DEFAULT_IMAGE = "zricethezav/gitleaks:v8.21.2"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the Gitleaks secret scan in Docker."
    )
    parser.add_argument(
        "--image",
        default=DEFAULT_IMAGE,
        help=f"Gitleaks image (default: {DEFAULT_IMAGE}).",
    )
    parser.add_argument(
        "--no-redact",
        action="store_true",
        help="Do not redact secrets in scan output.",
    )
    parser.add_argument(
        "--working-tree-only",
        action="store_true",
        help="Scan the working tree without Git history.",
    )
    return parser.parse_args()


def main() -> int:
    require_command("docker")
    args = parse_args()

    command = [
        "docker",
        "run",
        "--rm",
        "-v",
        f"{REPO_ROOT}:/repo",
        args.image,
        "detect",
        "--source=/repo",
        "--config=/repo/.gitleaks.toml",
        "--no-banner",
    ]

    if not args.no_redact:
        command.append("--redact")

    if args.working_tree_only:
        command.append("--no-git")

    run(command, cwd=REPO_ROOT)
    print("\nGitleaks scan PASSED.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
