#!/usr/bin/env python3
"""Run Catalogix frontend install/test/build operations."""

from __future__ import annotations

import argparse
from collections.abc import Callable

from common import (
    FRONTEND_DIR,
    clear_frontend_outputs,
    main_guard,
    require_command,
    require_file,
    run,
)


def install() -> None:
    """Install frontend dependencies using the available lockfile."""
    require_file(FRONTEND_DIR / "package.json", "Frontend package.json")

    lock_file = FRONTEND_DIR / "package-lock.json"
    command = ["npm", "ci"] if lock_file.is_file() else ["npm", "install"]

    run(command, cwd=FRONTEND_DIR)


def test() -> None:
    """Run the frontend test suite."""
    # package.json "test" is already `vitest run` (non-watch); no extra flags needed.
    run(["npm", "run", "test"], cwd=FRONTEND_DIR)


def coverage() -> None:
    """Run frontend tests with coverage."""
    run(["npm", "run", "test:coverage"], cwd=FRONTEND_DIR)


def build() -> None:
    """Build the frontend production bundle."""
    run(["npm", "run", "build"], cwd=FRONTEND_DIR)


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
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


def clear_outputs() -> None:
    """Clear frontend outputs and local cache and report what was removed."""
    removed = clear_frontend_outputs()

    if removed:
        print("Cleared frontend outputs/cache:")
        for item in removed:
            print(f"  - {item}")
    else:
        print("No frontend outputs/cache found.")


def run_clean() -> None:
    """Clean frontend build outputs and cache."""
    removed = clear_frontend_outputs()

    for item in removed:
        print(f"Removed {item}")


def run_with_optional_install(action: Callable[[], None], no_install: bool) -> None:
    """Run an action, optionally installing frontend dependencies first."""
    if not no_install:
        install()

    action()


def run_all(args: argparse.Namespace) -> None:
    """Run the complete frontend workflow."""
    if not args.no_install:
        install()

    test_command = coverage if args.coverage_in_all else test
    test_command()

    build()


def run_action(args: argparse.Namespace) -> None:
    """Dispatch the requested frontend operation."""
    handlers: dict[str, Callable[[], None]] = {
        "clean": run_clean,
        "install": install,
        "test": lambda: run_with_optional_install(test, args.no_install),
        "coverage": lambda: run_with_optional_install(coverage, args.no_install),
        "build": lambda: run_with_optional_install(build, args.no_install),
        "all": lambda: run_all(args),
    }

    handlers[args.action]()


def main() -> None:
    """Run the frontend test/build workflow."""
    require_command("npm")

    args = parse_args()

    if args.clear_cache:
        clear_outputs()

    run_action(args)


if __name__ == "__main__":
    main_guard(main)