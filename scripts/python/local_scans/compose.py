#!/usr/bin/env python3
"""Manage the Catalogix Docker Compose development stack."""

from __future__ import annotations

import argparse
from collections.abc import Callable

from common import (
    COMPOSE_FILE,
    REPO_ROOT,
    check_env_file,
    main_guard,
    require_docker,
    run,
)


def base_command() -> list[str]:
    """Return the base Docker Compose command."""
    return ["docker", "compose", "-f", str(COMPOSE_FILE)]


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Catalogix Docker Compose lifecycle manager."
    )
    parser.add_argument(
        "action",
        choices=[
            "start",
            "stop",
            "restart",
            "status",
            "logs",
            "build",
            "down",
            "pull",
            "config",
        ],
        help="start = build if needed + up -d. config = validate compose file and .env.",
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
        "--no-build",
        action="store_true",
        help="For start, do not build images first.",
    )
    parser.add_argument(
        "--remove-orphans",
        action="store_true",
        help="For down, also remove orphan containers.",
    )
    return parser.parse_args()


def validate_action(action: str) -> None:
    """Validate prerequisites required by specific actions."""
    env_required_actions = {"start", "build", "config", "restart"}

    if action in env_required_actions:
        check_env_file()


def build_start_command(
    args: argparse.Namespace,
    services: list[str],
) -> list[str]:
    """Build the Compose start command."""
    command = base_command() + ["up", "-d"]

    if not args.no_build:
        command.append("--build")

    return command + services


def build_stop_command(services: list[str]) -> list[str]:
    """Build the Compose stop command."""
    return base_command() + ["stop", *services]


def build_restart_command(services: list[str]) -> list[str]:
    """Build the Compose restart command."""
    return base_command() + ["restart", *services]


def build_status_command() -> list[str]:
    """Build the Compose status command."""
    return base_command() + ["ps", "--all"]


def build_logs_command(
    args: argparse.Namespace,
    services: list[str],
) -> list[str]:
    """Build the Compose logs command."""
    command = base_command() + ["logs", "--tail", "200"]

    if args.follow:
        command.append("-f")

    return command + services


def build_build_command(
    args: argparse.Namespace,
    services: list[str],
) -> list[str]:
    """Build the Compose image build command."""
    command = base_command() + ["build"]

    if args.no_cache:
        command.append("--no-cache")

    return command + services


def build_down_command(
    args: argparse.Namespace,
) -> list[str]:
    """Build the Compose down command."""
    command = base_command() + ["down"]

    if args.remove_orphans:
        command.append("--remove-orphans")

    return command


def build_pull_command(services: list[str]) -> list[str]:
    """Build the Compose pull command."""
    return base_command() + ["pull", *services]


def build_config_command() -> list[str]:
    """Build the Compose configuration validation command."""
    return base_command() + ["config", "--quiet"]


def build_command(
    args: argparse.Namespace,
    services: list[str],
) -> list[str]:
    """Build the Docker Compose command for the requested action."""
    handlers: dict[str, Callable[[], list[str]]] = {
        "start": lambda: build_start_command(args, services),
        "stop": lambda: build_stop_command(services),
        "restart": lambda: build_restart_command(services),
        "status": build_status_command,
        "logs": lambda: build_logs_command(args, services),
        "build": lambda: build_build_command(args, services),
        "down": lambda: build_down_command(args),
        "pull": lambda: build_pull_command(services),
        "config": build_config_command,
    }

    return handlers[args.action]()


def print_success_message(action: str) -> None:
    """Print any action-specific success message."""
    messages = {
        "config": "docker-compose.yaml and .env are valid.",
        "start": (
            "\nStack started. "
            "Gateway: http://localhost:11000  "
            "Mailpit: http://localhost:8025  "
            "RabbitMQ: http://localhost:25672"
        ),
    }

    message = messages.get(action)

    if message:
        print(message)


def main() -> int:
    """Run the Docker Compose lifecycle manager."""
    require_docker()

    args = parse_args()
    services = args.service or []

    validate_action(args.action)

    command = build_command(args, services)
    run(command, cwd=REPO_ROOT)

    print_success_message(args.action)

    return 0


if __name__ == "__main__":
    main_guard(main)