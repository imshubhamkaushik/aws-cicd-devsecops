#!/usr/bin/env python3
"""Run the complete local application validation workflow.

This script intentionally does NOT start Docker Compose.
Use compose.py for runtime/container lifecycle management.

Default:
    backend  -> mvn clean verify (complete Maven reactor)
    frontend -> npm ci + npm test + npm run build

--clear-cache removes project build outputs/Vite cache but does not delete
the Maven ~/.m2 repository or frontend node_modules.
"""

from __future__ import annotations

import argparse

from common import (
    REPO_ROOT,
    clear_backend_targets,
    clear_frontend_outputs,
    run_python,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build and test Catalogix backend and frontend locally."
    )
    parser.add_argument(
        "--clear-cache",
        action="store_true",
        help="Clear backend target directories and frontend build/Vite outputs first.",
    )
    parser.add_argument(
        "--backend-only",
        action="store_true",
        help="Only run the complete backend Maven clean verify.",
    )
    parser.add_argument(
        "--frontend-only",
        action="store_true",
        help="Only run frontend test + production build.",
    )
    parser.add_argument(
        "--frontend-coverage",
        action="store_true",
        help="Use frontend coverage tests in the full workflow.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.backend_only and args.frontend_only:
        raise RuntimeError("Choose at most one of --backend-only and --frontend-only.")

    if args.clear_cache:
        print("\nClearing project build/cache outputs...")
        removed_targets = clear_backend_targets()
        removed_frontend = clear_frontend_outputs()
        print(f"Removed {removed_targets} backend target directories.")
        for item in removed_frontend:
            print(f"Removed {item}")

    if not args.frontend_only:
        print("\n=== BACKEND: mvn clean verify ===")
        run_python("mvn.py", "clean-verify", "--all")

    if not args.backend_only:
        print("\n=== FRONTEND: test + build ===")
        frontend_args = ["all"]
        if args.frontend_coverage:
            frontend_args.append("--coverage-in-all")
        run_python("frontend.py", *frontend_args)

    print("\nCatalogix local build/test workflow PASSED.")
    print(f"Repository: {REPO_ROOT}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
