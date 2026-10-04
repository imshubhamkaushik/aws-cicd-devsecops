#!/usr/bin/env python3
"""Run the Catalogix Trivy security/misconfiguration scans in Docker."""

from __future__ import annotations

import argparse
import json
import os

from common import COMPOSE_FILE, REPO_ROOT, require_command, run


DEFAULT_IMAGE = os.getenv("TRIVY_IMAGE", "aquasec/trivy:0.70.0")


def trivy_base() -> list[str]:
    return [
        "docker",
        "run",
        "--rm",
        "-v",
        f"{REPO_ROOT}:/repo",
        DEFAULT_IMAGE,
    ]


def ignorefile_argument() -> list[str]:
    ignorefile = REPO_ROOT / ".trivyignore"
    if ignorefile.is_file():
        return ["--ignorefile", "/repo/.trivyignore"]
    return []


def compose_service_images() -> list[str]:
    result = run(
        [
            "docker",
            "compose",
            "-f",
            str(COMPOSE_FILE),
            "config",
            "--format",
            "json",
        ],
        capture=True,
    )

    data = json.loads(result.stdout)
    images = []

    for service in data.get("services", {}).values():
        if service.get("build") and service.get("image"):
            images.append(service["image"])

    return sorted(set(images))


def all_compose_images() -> list[str]:
    result = run(
        [
            "docker",
            "compose",
            "-f",
            str(COMPOSE_FILE),
            "config",
            "--images",
        ],
        capture=True,
    )
    return sorted(set(line.strip() for line in result.stdout.splitlines() if line.strip()))


def build_images() -> None:
    run(
        [
            "docker",
            "compose",
            "-f",
            str(COMPOSE_FILE),
            "build",
            "--parallel",
        ],
        cwd=REPO_ROOT,
    )


def scan_fs() -> None:
    command = trivy_base() + [
        "fs",
        "--scanners",
        "vuln,secret,misconfig",
        "--severity",
        "HIGH,CRITICAL",
        "--exit-code",
        "1",
        "--ignore-unfixed",
        *ignorefile_argument(),
        "/repo",
    ]
    run(command, cwd=REPO_ROOT)


def scan_config(target: str) -> None:
    command = trivy_base() + [
        "config",
        "--severity",
        "HIGH,CRITICAL",
        "--exit-code",
        "1",
        *ignorefile_argument(),
        target,
    ]
    run(command, cwd=REPO_ROOT)


def scan_image(image: str) -> None:
    command = [
        "docker",
        "run",
        "--rm",
        "-v",
        "/var/run/docker.sock:/var/run/docker.sock",
        "-v",
        f"{REPO_ROOT}:/repo",
        DEFAULT_IMAGE,
        "image",
        "--severity",
        "HIGH,CRITICAL",
        "--exit-code",
        "1",
        "--ignore-unfixed",
        *ignorefile_argument(),
        image,
    ]
    run(command, cwd=REPO_ROOT)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run Catalogix Trivy scans with Docker."
    )
    parser.add_argument(
        "target",
        choices=["fs", "config", "images", "all"],
        help="Scan target.",
    )
    parser.add_argument(
        "--build",
        action="store_true",
        help="Build all Compose application images before image scanning.",
    )
    parser.add_argument(
        "--all-images",
        action="store_true",
        help="Scan all Compose images, including Postgres/RabbitMQ/Mailpit, not just application build images.",
    )
    parser.add_argument(
        "--no-ignore-unfixed",
        action="store_true",
        help="Do not exclude vulnerabilities for which no fix is available.",
    )
    return parser.parse_args()


def scan_image_with_options(image: str, ignore_unfixed: bool) -> None:
    command = [
        "docker",
        "run",
        "--rm",
        "-v",
        "/var/run/docker.sock:/var/run/docker.sock",
        "-v",
        f"{REPO_ROOT}:/repo",
        DEFAULT_IMAGE,
        "image",
        "--severity",
        "HIGH,CRITICAL",
        "--exit-code",
        "1",
    ]
    if ignore_unfixed:
        command.append("--ignore-unfixed")
    command += ignorefile_argument()
    command.append(image)
    run(command, cwd=REPO_ROOT)


def main() -> int:
    require_command("docker")
    args = parse_args()

    if args.target in ("fs", "all"):
        print("\n=== TRIVY FILESYSTEM ===")
        scan_fs()

    if args.target in ("config", "all"):
        print("\n=== TRIVY TERRAFORM ===")
        if (REPO_ROOT / "terraform").is_dir():
            scan_config("/repo/terraform")
        else:
            print("No terraform directory found; skipping.")

        print("\n=== TRIVY HELM/KUBERNETES ===")
        if (REPO_ROOT / "helm").is_dir():
            scan_config("/repo/helm")
        else:
            print("No helm directory found; skipping.")

    if args.target in ("images", "all"):
        if args.build:
            print("\n=== DOCKER COMPOSE BUILD ===")
            build_images()

        images = (
            all_compose_images()
            if args.all_images
            else compose_service_images()
        )

        if not images:
            raise RuntimeError(
                "No local Compose application images were found. "
                "Run with --build or docker compose build first."
            )

        print("\n=== TRIVY IMAGE SCANS ===")
        for image in images:
            print(f"\n--- {image} ---")
            scan_image_with_options(
                image,
                ignore_unfixed=not args.no_ignore_unfixed,
            )

    print("\nTrivy scan workflow PASSED.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
