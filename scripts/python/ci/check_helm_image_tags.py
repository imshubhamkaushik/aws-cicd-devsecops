#!/usr/bin/env python3
"""Contract check: every Helm application image must honour per-service repository/tag overrides."""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
CHART = REPO_ROOT / "infra" / "helm" / "catalogix-hc"
REGISTRY = "registry.example.test"
GLOBAL_TAG = "GLOBAL-SENTINEL"
# This list is an explicit chart/pipeline contract. If a new backend appears in values-dev.yaml
# but is missing here, its rendered image retains GLOBAL-SENTINEL and the fallback check fails.
BACKEND_SERVICES = (
    "user-svc",
    "catalog-svc",
    "inventory-svc",
    "cart-svc",
    "payment-svc",
    "checkout-svc",
    "notification-svc",
)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "values_file", nargs="?", type=Path, default=CHART / "values-dev.yaml",
        help="Helm values file (default: infra/helm/catalogix-hc/values-dev.yaml)",
    )
    return parser.parse_args(argv)


def render_chart(values_file: Path, services: list[str]) -> str:
    helm = shutil.which("helm")
    if not helm:
        raise RuntimeError("helm was not found on PATH.")
    args = [
        helm, "template", "catalogix", str(CHART), "--namespace", "catalogix", "-f", str(values_file),
        "--set", f"global.imageRegistry={REGISTRY}",
        "--set", f"global.imageTag={GLOBAL_TAG}",
        "--set", "global.cloudProvider=aws",
    ]
    for service in services:
        args.extend(["--set", f"backendServices.{service}.image.repository=catalogix-{service}"])
        args.extend(["--set", f"backendServices.{service}.image.tag=tag-{service}"])
    for service in ("frontend", "gateway"):
        args.extend(["--set", f"{service}.image.repository=catalogix-{service}"])
        args.extend(["--set", f"{service}.image.tag=tag-{service}"])

    result = subprocess.run(args, cwd=REPO_ROOT, text=True, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"helm template exited {result.returncode}")
    return result.stdout


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    values_file = args.values_file
    if not values_file.is_absolute():
        values_file = (Path.cwd() / values_file).resolve()
    if not values_file.is_file():
        print(f"ERROR: values file not found: {values_file}", file=sys.stderr)
        return 2

    try:
        # Read the file to fail early on a missing/unreadable input. The values themselves are
        # interpreted by Helm, keeping this check standard-library-only on Jenkins agents.
        values_file.read_text(encoding="utf-8")
        rendered = render_chart(values_file, list(BACKEND_SERVICES))
    except (OSError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    services = [*BACKEND_SERVICES, "frontend", "gateway"]
    failed = False
    image_lines = [line.strip() for line in rendered.splitlines() if re.match(r"^\s*image\s*:", line)]
    for service in services:
        expected = f'image: "{REGISTRY}/catalogix-{service}:tag-{service}"'
        if expected not in image_lines:
            print(f"FAIL: {service}: expected {expected} in rendered manifests")
            failed = True

    fallback = [line for line in image_lines if line.endswith(f":{GLOBAL_TAG}\"") or line.endswith(f":{GLOBAL_TAG}")]
    if fallback:
        print("FAIL: image(s) ignored per-service image-tag overrides and fell back to global.imageTag:")
        for line in fallback:
            print(f"  {line}")
        failed = True

    if failed:
        return 1
    print(f"OK: all {len(services)} images honour their per-service repository/tag values.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
