#!/usr/bin/env python3
"""Run targeted or full Maven test/build/verify operations for Catalogix."""

from __future__ import annotations

import argparse

from common import REPO_ROOT, maven_modules, resolve_services, run


def build_command(
    action: str,
    services: list[str],
    *,
    all_modules: bool,
    clean: bool,
    with_tests: bool,
    no_deps: bool,
) -> list[str]:
    command = ["mvn", "-B"]

    if clean:
        command.append("clean")

    if all_modules:
        command.append(action)
    else:
        command.extend(["-pl", ",".join(f"backend/{svc}" for svc in services)])
        if not no_deps:
            command.append("-am")
        command.append(action)

    if action == "build" and not with_tests:
        command.append("-DskipTests")

    return command


def parse_args() -> argparse.Namespace:
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
        "--list-services",
        action="store_true",
        help="List backend Maven services and exit.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.list_services:
        for service in sorted(maven_modules()):
            print(service)
        return 0

    # "clean-verify" deliberately maps to the user's preferred full-reactor command.
    if args.action == "clean-verify":
        services = resolve_services(args.services)
        if args.all or not services:
            run(["mvn", "-B", "clean", "verify"], cwd=REPO_ROOT)
        else:
            run(
                build_command(
                    "verify",
                    services,
                    all_modules=False,
                    clean=True,
                    with_tests=True,
                    no_deps=args.no_deps,
                ),
                cwd=REPO_ROOT,
            )
        return 0

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

    run(command, cwd=REPO_ROOT)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
