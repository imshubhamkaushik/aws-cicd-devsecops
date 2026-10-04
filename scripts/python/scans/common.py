#!/usr/bin/env python3
"""Shared helpers for Catalogix local automation scripts."""

from __future__ import annotations

import subprocess
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable, Sequence


REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_DIR = REPO_ROOT / "backend"
FRONTEND_DIR = REPO_ROOT / "frontend"
ROOT_POM_FILE = REPO_ROOT / "pom.xml"
COMPOSE_FILE = REPO_ROOT / "docker-compose.yaml"


def run(
    command: Sequence[str],
    *,
    cwd: Path | None = None,
    capture: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    display = " ".join(
        f'"{part}"' if " " in str(part) else str(part)
        for part in command
    )
    print(f"\n> {display}")

    return subprocess.run(
        [str(part) for part in command],
        cwd=str(cwd or REPO_ROOT),
        text=True,
        capture_output=capture,
        check=check,
    )


def require_command(name: str) -> None:
    if shutil.which(name) is None:
        raise RuntimeError(f"'{name}' was not found on PATH.")


def require_file(path: Path, description: str) -> None:
    if not path.is_file():
        raise RuntimeError(f"{description} not found: {path}")


def require_directory(path: Path, description: str) -> None:
    if not path.is_dir():
        raise RuntimeError(f"{description} not found: {path}")


def maven_modules() -> list[str]:
    """Return backend Maven module directory names from the root POM."""
    require_file(ROOT_POM_FILE, "Root pom.xml")

    root = ET.parse(ROOT_POM_FILE).getroot()
    modules = []

    for element in root.findall("./modules/module"):
        value = (element.text or "").strip().replace("\\", "/")
        if value.startswith("backend/"):
            modules.append(Path(value).name)

    if not modules:
        raise RuntimeError("No backend modules were found in pom.xml.")

    return modules


def resolve_services(values: Iterable[str] | None) -> list[str]:
    available = set(maven_modules())
    requested = [value.strip() for value in (values or []) if value.strip()]

    unknown = sorted(set(requested) - available)
    if unknown:
        raise RuntimeError(
            "Unknown backend service(s): "
            + ", ".join(unknown)
            + "\nAvailable: "
            + ", ".join(sorted(available))
        )

    return requested


def run_python(script_name: str, *args: str) -> None:
    script = Path(__file__).with_name(script_name)
    run([sys.executable, str(script), *args])


def clear_backend_targets() -> int:
    removed = 0
    for target in BACKEND_DIR.glob("*/target"):
        if target.is_dir():
            shutil.rmtree(target)
            removed += 1
    return removed


def clear_frontend_outputs() -> list[str]:
    removed: list[str] = []
    candidates = [
        FRONTEND_DIR / "dist",
        FRONTEND_DIR / "coverage",
        FRONTEND_DIR / "node_modules" / ".vite",
    ]
    for path in candidates:
        if path.exists():
            if path.is_dir():
                shutil.rmtree(path)
            else:
                path.unlink()
            removed.append(str(path.relative_to(REPO_ROOT)))
    return removed
