#!/usr/bin/env python3
"""Run targeted or full Maven test/build/verify operations for Catalogix."""

from __future__ import annotations

import argparse

from common import (
    REPO_ROOT,
    main_guard,
    maven_modules,
    require_command,
    require_docker,
    resolve_services,
    run,
)


def build_command(
    action: str,
    services: list[str],
    *,
    all_modules: bool,
    clean: bool,
    with_tests: bool,
    no_deps: bool,
) -> list[str]:
    """Build the Maven command for the requested operation."""
    command = ["mvn", "-B"]

    if clean:
        command.append("clean")

    if all_modules:
        command.append(action)
    else:
        command.extend(
            ["-pl", ",".join(f"backend/{service}" for service in services)]
        )
        if not no_deps:
            command.append("-am")
        command.append(action)

    if action == "build" and not with_tests:
        command.append("-DskipTests")

    return command


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Catalogix Maven test/build runner."
    )

    parser.add_argument(
        "action",
        choices=["test", "build", "verify", "clean-verify"],
        help="Operation to run.",
    )

    selection = parser.add_mutually_exclusive_group()
    selection.add_argument(
        "--service",
        action="append",
        dest="services",
        help="Backend service to operate on. Repeat for multiple services.",
    )
    selection.add_argument(
        "--all",
        action="store_true",
        help="Operate on the complete Maven reactor.",
    )

    parser.add_argument(
        "--clean",
        action="store_true",
        help="Run Maven clean before test/build/verify.",
    )
    parser.add_argument(
        "--with-tests",
        action="store_true",
        help="For 'build', also execute tests instead of using -DskipTests.",
    )
    parser.add_argument(
        "--no-deps",
        action="store_true",
        help="For targeted service operations, do not include Maven upstream modules with -am.",
    )
    parser.add_argument(
        "--skip-it",
        action="store_true",
        help="Skip Testcontainers integration tests (-DskipITs). Unit tests still run and Docker is not needed.",
    )
    parser.add_argument(
        "--list-services",
        action="store_true",
        help="List backend Maven services and exit.",
    )

    return parser.parse_args()


def list_services() -> None:
    """Print available backend Maven services."""
    for service in sorted(maven_modules()):
        print(service)


def run_clean_verify(
    args: argparse.Namespace,
    extra: list[str],
) -> None:
    """Run the clean-verify operation."""
    # `clean-verify` is the full-reactor `mvn clean verify` checkpoint.
    services = resolve_services(args.services)

    if args.all or not services:
        run(
            ["mvn", "-B", "clean", "verify", *extra],
            cwd=REPO_ROOT,
        )
        return

    command = build_command(
        "verify",
        services,
        all_modules=False,
        clean=True,
        with_tests=True,
        no_deps=args.no_deps,
    )

    run(
        [*command, *extra],
        cwd=REPO_ROOT,
    )


def run_standard_action(
    args: argparse.Namespace,
    extra: list[str],
) -> None:
    """Run test, build, or verify for the selected services."""
    services = resolve_services(args.services)

    # If neither --service nor --all is supplied, default to the full reactor.
    all_modules = args.all or not services

    command = build_command(
        args.action,
        services,
        all_modules=all_modules,
        clean=args.clean,
        with_tests=args.with_tests,
        no_deps=args.no_deps,
    )

    run(
        [*command, *extra],
        cwd=REPO_ROOT,
    )


def main() -> int:
    """Run the Maven test/build/verify workflow."""
    args = parse_args()

    require_command(
        "mvn",
        "Install Maven 3.9+ and JDK 21 and add them to PATH.",
    )

    if args.list_services:
        list_services()
    else:
        if args.action in ("verify", "clean-verify") and not args.skip_it:
            require_docker()

        extra = ["-DskipITs"] if args.skip_it else []

        if args.action == "clean-verify":
            run_clean_verify(args, extra)
        else:
            run_standard_action(args, extra)

    return 0


if __name__ == "__main__":
    main_guard(main)