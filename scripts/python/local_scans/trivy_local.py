#!/usr/bin/env python3
"""Run the Catalogix Trivy scans locally in Docker, mirroring the CI pipelines.

Targets
  fs      source tree: HIGH/CRITICAL vulnerabilities (dependencies), secrets, misconfigurations
  config  Terraform (terraform/) and the Helm chart rendered with the dev AND local values
  images  the application images built by docker-compose.yaml
  all     everything above

The Trivy version comes from ansible/group_vars/all/vars.yaml (same as Jenkins).
A named Docker volume caches the vulnerability DB so only the first run downloads it.
"""

from __future__ import annotations

import argparse
import json
import os

from common import (
    COMPOSE_FILE,
    HELM_CHART,
    REPO_MOUNT,
    REPO_ROOT,
    check_env_file,
    main_guard,
    require_docker,
    run,
    tool_version,
)

CACHE_VOLUME = "catalogix-trivy-cache"
SEVERITY = "HIGH,CRITICAL"
SKIP_DIRS = "**/node_modules,**/target,**/.terraform,**/build,**/coverage,**/.git,**/__pycache__,**/.ruff_cache"
# Git-ignored local files; they hold throw-away local credentials and never reach CI or an image.
SKIP_FILES = "**/.env,secrets/*.txt,**/*rendered*.yaml"


def trivy_image() -> str:
    return os.getenv("TRIVY_IMAGE") or f"aquasec/trivy:{tool_version('trivy_version')}"


def trivy_base(*, docker_socket: bool = False) -> list[str]:
    cmd = ["docker", "run", "--rm", "-v", REPO_MOUNT, "-v", f"{CACHE_VOLUME}:/root/.cache/"]
    if docker_socket:
        cmd += ["-v", "/var/run/docker.sock:/var/run/docker.sock"]
    cmd.append(trivy_image())
    return cmd


def ignorefile_argument() -> list[str]:
    if (REPO_ROOT / ".trivyignore").is_file():
        return ["--ignorefile", "/repo/.trivyignore"]
    return []


def compose_service_images(include_third_party: bool) -> list[str]:
    result = run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "config", "--format", "json"],
        capture=True,
        quiet=True,
    )
    services = json.loads(result.stdout).get("services", {})
    images = {
        svc["image"]
        for svc in services.values()
        if svc.get("image") and (include_third_party or svc.get("build"))
    }
    return sorted(images)


def build_images() -> None:
    check_env_file()
    run(["docker", "compose", "-f", str(COMPOSE_FILE), "build"], cwd=REPO_ROOT)


def scan_fs() -> None:
    run(
        trivy_base() + [
            "fs", "--scanners", "vuln,secret,misconfig",
            "--severity", SEVERITY, "--exit-code", "1", "--ignore-unfixed",
            "--skip-dirs", SKIP_DIRS, "--skip-files", SKIP_FILES,
            *ignorefile_argument(), "/repo",
        ],
        cwd=REPO_ROOT,
    )


def scan_terraform() -> None:
    run(
        trivy_base() + [
            "config", "--severity", SEVERITY, "--exit-code", "1",
            "--skip-dirs", "**/.terraform", *ignorefile_argument(), "/repo/terraform",
        ],
        cwd=REPO_ROOT,
    )


def scan_helm() -> None:
    """The chart has no values.yaml, so Trivy needs a values file to render it.

    dev  -> the AWS code path (ExternalSecret, ALB ingress)
    dev + local -> the local code path (in-cluster Postgres, generated secrets)
    """
    chart = HELM_CHART.relative_to(REPO_ROOT).as_posix()
    for label, values in (
        ("AWS (values-dev)", [f"/repo/{chart}/values-dev.yaml"]),
        ("local (values-dev + values-local)", [f"/repo/{chart}/values-dev.yaml", f"/repo/{chart}/values-local.yaml"]),
    ):
        print(f"\n--- Helm chart rendered with {label} ---")
        helm_values: list[str] = []
        for v in values:
            helm_values += ["--helm-values", v]
        run(
            trivy_base() + [
                "config", "--severity", SEVERITY, "--exit-code", "1",
                *helm_values, *ignorefile_argument(), f"/repo/{chart}",
            ],
            cwd=REPO_ROOT,
        )


def scan_image(image: str, ignore_unfixed: bool) -> None:
    cmd = trivy_base(docker_socket=True) + ["image", "--severity", SEVERITY, "--exit-code", "1"]
    if ignore_unfixed:
        cmd.append("--ignore-unfixed")
    run(cmd + ignorefile_argument() + [image], cwd=REPO_ROOT)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Catalogix Trivy scans with Docker.")
    parser.add_argument("target", choices=["fs", "config", "images", "all"], help="Scan target.")
    parser.add_argument("--build", action="store_true", help="docker compose build before scanning images.")
    parser.add_argument("--all-images", action="store_true",
                        help="Also scan third-party images (postgres, rabbitmq, mailpit).")
    parser.add_argument("--no-ignore-unfixed", action="store_true",
                        help="Also report vulnerabilities that have no fix yet.")
    return parser.parse_args()


def main() -> int:
    require_docker()
    args = parse_args()

    if args.target in ("fs", "all"):
        print("\n=== TRIVY FILESYSTEM (vuln, secret, misconfig) ===")
        scan_fs()

    if args.target in ("config", "all"):
        print("\n=== TRIVY TERRAFORM ===")
        scan_terraform()
        print("\n=== TRIVY HELM CHART ===")
        scan_helm()

    if args.target in ("images", "all"):
        if args.build:
            print("\n=== DOCKER COMPOSE BUILD ===")
            build_images()
        images = compose_service_images(args.all_images)
        print("\n=== TRIVY IMAGE SCANS ===")
        for image in images:
            exists = run(["docker", "image", "inspect", image], capture=True, check=False, quiet=True)
            if exists.returncode != 0 and not args.all_images:
                raise RuntimeError(f"Image {image} not found locally. Re-run with --build.")
            print(f"\n--- {image} ---")
            scan_image(image, ignore_unfixed=not args.no_ignore_unfixed)

    print("\nTrivy scan workflow PASSED.")
    return 0


if __name__ == "__main__":
    main_guard(main)
