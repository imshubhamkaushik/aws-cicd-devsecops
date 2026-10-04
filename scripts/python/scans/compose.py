#!/usr/bin/env python3
"""Manage the Catalogix Docker Compose development stack."""

from __future__ import annotations

import argparse

from common import COMPOSE_FILE, REPO_ROOT, require_command, run


def base_command(profile: str | None = None) -> list[str]:
    command = ["docker", "compose", "-f", str(COMPOSE_FILE)]
    if profile:
        command.extend(["--profile", profile])
    return command


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Catalogix Docker Compose lifecycle manager."
    )
    parser.add_argument(
        "action",
        choices=["start", "stop", "restart", "status", "logs", "build", "down", "pull"],
    )
    parser.add_argument(
        "--service",
        action="append",
        help="Operate on one or more Compose services.",
    )
    parser.add_argument(
        "--follow",
        action="store_true",
        help="Follow logs for the 'logs' action.",
    )
    parser.add_argument(
        "--no-cache",
        action="store_true",
        help="For build, disable Docker build cache.",
    )
    parser.add_argument(
        "--remove-orphans",
        action="store_true",
        help="For down, also remove orphan containers.",
    )
    return parser.parse_args()


def main() -> int:
    require_command("docker")
    args = parse_args()

    command = base_command()

    services = args.service or []

    if args.action == "start":
        command += ["up", "-d", *services]
    elif args.action == "stop":
        command += ["stop", *services]
    elif args.action == "restart":
        command += ["restart", *services]
    elif args.action == "status":
        command += ["ps"]
    elif args.action == "logs":
        command += ["logs"]
        if args.follow:
            command.append("-f")
        command += services
    elif args.action == "build":
        command += ["build", "--parallel"]
        if args.no_cache:
            command.append("--no-cache")
        command += services
    elif args.action == "down":
        command += ["down"]
        if args.remove_orphans:
            command.append("--remove-orphans")
    elif args.action == "pull":
        command += ["pull"]
        command += services

    run(command, cwd=REPO_ROOT)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
