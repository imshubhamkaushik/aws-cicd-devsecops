#!/usr/bin/env python3
"""The complete local pre-AWS gate. Each step is also runnable on its own.

Order (fail fast, cheapest first):
    1. Gitleaks            secrets in the tree / history
    2. mvn clean verify    backend unit + integration tests
    3. npm ci/test/build   frontend, with coverage for SonarQube
    4. SonarQube           analysis + quality gate
    5. Trivy               source, Terraform, Helm chart, built images
    6. Docker Compose      build + start the stack, smoke test, optional shutdown
"""

from __future__ import annotations

import argparse

from common import main_guard, run_python


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the complete local Catalogix DevSecOps gate.")
    parser.add_argument("--clear-cache", action="store_true", help="Clear build outputs before tests.")
    parser.add_argument("--start-sonarqube", action="store_true",
                        help="Start/reuse a managed local SonarQube container and create its token.")
    parser.add_argument("--skip-sonar", action="store_true")
    parser.add_argument("--skip-trivy", action="store_true")
    parser.add_argument("--skip-gitleaks", action="store_true")
    parser.add_argument("--skip-smoke", action="store_true", help="Skip compose start + smoke test.")
    parser.add_argument("--shutdown", action="store_true", help="Stop the Compose stack after the smoke test.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if not args.skip_gitleaks:
        print("\n##### 1/6 GITLEAKS #####")
        run_python("gitleaks.py")

    print("\n##### 2-3/6 BUILD AND TEST #####")
    local = ["--frontend-coverage"]  # lcov.info is required by the Sonar step
    if args.clear_cache:
        local.append("--clear-cache")
    run_python("run_local.py", *local)

    if not args.skip_sonar:
        print("\n##### 4/6 SONARQUBE #####")
        # Reports were just produced above, so no --build here.
        run_python("sonarqube_local.py", *(["--start-server"] if args.start_sonarqube else []))

    if not args.skip_trivy:
        print("\n##### 5/6 TRIVY #####")
        run_python("trivy_local.py", "all", "--build")

    if not args.skip_smoke:
        print("\n##### 6/6 COMPOSE + SMOKE TEST #####")
        run_python("compose.py", "start", "--no-build")  # images were built by the Trivy step
        run_python("smoke_test.py")
        if args.shutdown:
            run_python("compose.py", "stop")

    print("\nCatalogix DevSecOps gate PASSED.")
    return 0


if __name__ == "__main__":
    main_guard(main)
