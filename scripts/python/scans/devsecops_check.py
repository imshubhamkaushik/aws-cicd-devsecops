#!/usr/bin/env python3
"""Run a complete local Catalogix DevSecOps gate.

Order:
    Gitleaks
    Backend clean verify
    Frontend tests/build
    SonarQube analysis
    Trivy filesystem/IaC/image scans
    Docker Compose startup
    Smoke tests

This script deliberately composes the smaller scripts so each check can also
be run independently.
"""

from __future__ import annotations

import argparse

from common import run_python


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the complete local Catalogix DevSecOps validation gate."
    )
    parser.add_argument(
        "--clear-cache",
        action="store_true",
        help="Clear project build outputs before tests.",
    )
    parser.add_argument(
        "--start-sonarqube",
        action="store_true",
        help="Start/reuse the managed SonarQube Docker container.",
    )
    parser.add_argument(
        "--skip-sonar",
        action="store_true",
        help="Skip SonarQube.",
    )
    parser.add_argument(
        "--skip-trivy",
        action="store_true",
        help="Skip Trivy.",
    )
    parser.add_argument(
        "--skip-gitleaks",
        action="store_true",
        help="Skip Gitleaks.",
    )
    parser.add_argument(
        "--skip-smoke",
        action="store_true",
        help="Skip runtime smoke tests.",
    )
    parser.add_argument(
        "--shutdown",
        action="store_true",
        help="Stop Compose containers after smoke tests.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if not args.skip_gitleaks:
        run_python("gitleaks.py")

    local_args = ["--backend-only"]
    if args.clear_cache:
        local_args.append("--clear-cache")
    run_python("run_local.py", *local_args)

    frontend_args = ["--frontend-only"]
    if args.clear_cache:
        frontend_args.append("--clear-cache")
    run_python("run_local.py", *frontend_args)

    if not args.skip_sonar:
        sonar_args = []
        if args.start_sonarqube:
            sonar_args.append("--start-server")
        run_python("sonarqube.py", *sonar_args)

    if not args.skip_trivy:
        run_python("trivy.py", "all", "--build")

    if not args.skip_smoke:
        run_python("compose.py", "start")
        run_python("smoke_test.py")
        if args.shutdown:
            run_python("compose.py", "stop")

    print("\nCatalogix DevSecOps gate PASSED.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
