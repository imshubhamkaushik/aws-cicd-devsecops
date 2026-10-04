#!/usr/bin/env python3
"""Run Catalogix frontend install/test/build operations."""

from __future__ import annotations

import argparse

from common import FRONTEND_DIR, clear_frontend_outputs, run, require_command, require_file


def install() -> None:
    require_file(FRONTEND_DIR / "package.json", "Frontend package.json")
    lock_file = FRONTEND_DIR / "package-lock.json"
    command = ["npm", "ci"] if lock_file.is_file() else ["npm", "install"]
    run(command, cwd=FRONTEND_DIR)


def test() -> None:
    run(["npm", "run", "test", "--", "--run"], cwd=FRONTEND_DIR)


def coverage() -> None:
    run(["npm", "run", "test:coverage"], cwd=FRONTEND_DIR)


def build() -> None:
    run(["npm", "run", "build"], cwd=FRONTEND_DIR)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Catalogix frontend test/build runner."
    )
    parser.add_argument(
        "action",
        choices=["install", "test", "coverage", "build", "all", "clean"],
        help="Frontend operation.",
    )
    parser.add_argument(
        "--no-install",
        action="store_true",
        help="Do not run npm ci/install before test/build/all.",
    )
    parser.add_argument(
        "--clear-cache",
        action="store_true",
        help="Clear dist, coverage, and Vite's local cache before running.",
    )
    parser.add_argument(
        "--coverage-in-all",
        action="store_true",
        help="Use npm run test:coverage in the 'all' workflow instead of npm test.",
    )
    return parser.parse_args()


def main() -> int:
    require_command("npm")
    args = parse_args()

    if args.clear_cache:
        removed = clear_frontend_outputs()
        print("Cleared frontend outputs/cache:" if removed else "No frontend outputs/cache found.")
        for item in removed:
            print(f"  - {item}")

    if args.action == "clean":
        removed = clear_frontend_outputs()
        for item in removed:
            print(f"Removed {item}")
        return 0

    if args.action == "install":
        install()
        return 0

    if args.action == "test":
        if not args.no_install:
            install()
        test()
        return 0

    if args.action == "coverage":
        if not args.no_install:
            install()
        coverage()
        return 0

    if args.action == "build":
        if not args.no_install:
            install()
        build()
        return 0

    if not args.no_install:
        install()

    if args.coverage_in_all:
        coverage()
    else:
        test()

    build()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
