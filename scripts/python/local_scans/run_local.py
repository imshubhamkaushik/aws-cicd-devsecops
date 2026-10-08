#!/usr/bin/env python3
"""Local build + test checkpoints: `mvn clean verify` and the frontend `npm` workflow.

Default:
    backend  -> mvn clean verify   (unit tests + Testcontainers integration tests; needs Docker running)
    frontend -> npm ci + npm test + npm run build   (with --frontend-coverage: coverage instead of plain test)

--clear-cache removes project build outputs (backend/*/target, frontend build/coverage and the
Vite cache) but never ~/.m2 or frontend/node_modules.
This script does not start Docker Compose; use compose.py for that.
"""

from __future__ import annotations

import argparse

from common import REPO_ROOT, clear_backend_targets, clear_frontend_outputs, main_guard, run_python


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build and test Catalogix backend and frontend locally.")
    parser.add_argument("--clear-cache", action="store_true", help="Clear target/build/coverage outputs first.")
    scope = parser.add_mutually_exclusive_group()
    scope.add_argument("--backend-only", action="store_true", help="Only mvn clean verify.")
    scope.add_argument("--frontend-only", action="store_true", help="Only npm ci + test + build.")
    parser.add_argument("--frontend-coverage", action="store_true",
                        help="Produce frontend/coverage/lcov.info (needed by SonarQube).")
    parser.add_argument("--skip-it", action="store_true",
                        help="Backend: skip Testcontainers integration tests (no Docker needed).")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.clear_cache:
        print("\nClearing project build/cache outputs...")
        print(f"Removed {clear_backend_targets()} backend target directories.")
        for item in clear_frontend_outputs():
            print(f"Removed {item}")

    if not args.frontend_only:
        print("\n=== BACKEND: mvn clean verify ===")
        mvn_args = ["clean-verify", "--all"]
        if args.skip_it:
            mvn_args.append("--skip-it")
        run_python("mvn.py", *mvn_args)

    if not args.backend_only:
        print("\n=== FRONTEND: npm ci, test, build ===")
        npm_args = ["all"]
        if args.frontend_coverage:
            npm_args.append("--coverage-in-all")
        run_python("npm.py", *npm_args)

    print("\nCatalogix local build/test workflow PASSED.")
    print(f"Repository: {REPO_ROOT}")
    return 0


if __name__ == "__main__":
    main_guard(main)
