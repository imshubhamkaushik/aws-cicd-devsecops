#!/usr/bin/env python3
"""Shared helpers for Catalogix local automation scripts.

Design rules for every script in this folder:
  * Tool versions come from ansible/group_vars/all/vars.yaml, the same file the
    Jenkins pipelines read, so a local run and a CI run use the same scanners.
  * Every external tool is resolved with shutil.which, so `mvn.cmd` / `npm.cmd`
    work on Windows (a bare "mvn" is not found by subprocess there).
  * Failures say what to do next (start Docker Desktop, fix .env, ...).
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable, Sequence


REPO_ROOT = Path(__file__).resolve().parents[3]
BACKEND_DIR = REPO_ROOT / "backend"
FRONTEND_DIR = REPO_ROOT / "frontend"
ROOT_POM_FILE = REPO_ROOT / "pom.xml"
COMPOSE_FILE = REPO_ROOT / "docker-compose.yaml"
ENV_FILE = REPO_ROOT / ".env"
ENV_EXAMPLE_FILE = REPO_ROOT / ".env.example"
VERSIONS_FILE = REPO_ROOT / "ansible" / "group_vars" / "all" / "vars.yaml"
HELM_CHART = REPO_ROOT / "helm" / "catalogix-hc"

# Mount path inside scanner containers. Posix form so "C:\\Users\\me\\repo" works with
# Docker Desktop on Windows (`-v C:/Users/me/repo:/repo`).
REPO_MOUNT = f"{REPO_ROOT.as_posix()}:/repo"


def run(
    command: Sequence[object],
    *,
    cwd: Path | None = None,
    capture: bool = False,
    check: bool = True,
    env: dict[str, str] | None = None,
    quiet: bool = False,
) -> subprocess.CompletedProcess[str]:
    """Run a command (no shell). The executable is resolved through PATH/PATHEXT."""
    parts = [str(part) for part in command]
    resolved = shutil.which(parts[0])
    if resolved is None:
        raise RuntimeError(f"'{parts[0]}' was not found on PATH.")
    parts[0] = resolved

    if not quiet:
        shown = " ".join(f'"{p}"' if " " in p else p for p in parts)
        # Never print secrets passed as -e NAME=value
        shown = re.sub(r"(SONAR_TOKEN|PASSWORD|SECRET)=\S+", r"\1=***", shown)
        print(f"\n> {shown}", flush=True)

    merged_env = {**os.environ, **(env or {})}
    try:
        return subprocess.run(  # noqa: S603
            parts,
            cwd=str(cwd or REPO_ROOT),
            text=True,
            capture_output=capture,
            check=check,
            env=merged_env,
        )
    except subprocess.CalledProcessError as exc:
        # Keep the exit code visible; the caller's main() turns it into a clean message.
        raise RuntimeError(f"Command failed with exit code {exc.returncode}: {parts[0]}") from exc


def require_command(name: str, hint: str = "") -> None:
    if shutil.which(name) is None:
        raise RuntimeError(f"'{name}' was not found on PATH." + (f" {hint}" if hint else ""))


def require_file(path: Path, description: str) -> None:
    if not path.is_file():
        raise RuntimeError(f"{description} not found: {path}")


def require_directory(path: Path, description: str) -> None:
    if not path.is_dir():
        raise RuntimeError(f"{description} not found: {path}")


def require_docker() -> None:
    """Fail early (and clearly) when the Docker CLI exists but the daemon is not running."""
    require_command("docker", "Install Docker Desktop.")
    result = subprocess.run(  # noqa: S603,S607
        [shutil.which("docker") or "docker", "info"],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "Docker is installed but the daemon is not reachable. "
            "Start Docker Desktop, wait for 'Engine running', then retry."
        )


def tool_version(key: str, env_override: str | None = None) -> str:
    """Read a pinned tool version from ansible/group_vars/all/vars.yaml (the CI source of truth)."""
    if env_override and os.getenv(env_override):
        return os.environ[env_override].strip()
    require_file(VERSIONS_FILE, "Tool version file")
    pattern = re.compile(rf'^{re.escape(key)}:\s*"?([^"\s#]+)"?')
    for line in VERSIONS_FILE.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line)
        if match:
            return match.group(1)
    raise RuntimeError(f"'{key}' not found in {VERSIONS_FILE}")


def parse_env_keys(path: Path) -> set[str]:
    keys: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            keys.add(line.split("=", 1)[0].strip())
    return keys


def check_env_file() -> None:
    """Make sure .env has every key .env.example defines.

    A stale .env is the most common reason `docker compose up` fails halfway
    (for example a missing SERVICE_DB_PASSWORD stops the Postgres init script).
    """
    if not ENV_FILE.is_file():
        raise RuntimeError("No .env found. Run: cp .env.example .env  (then set the secrets in it)")
    if not ENV_EXAMPLE_FILE.is_file():
        return
    missing = sorted(parse_env_keys(ENV_EXAMPLE_FILE) - parse_env_keys(ENV_FILE))
    if missing:
        raise RuntimeError(
            ".env is missing key(s) defined in .env.example: "
            + ", ".join(missing)
            + "\nAdd them (compare with .env.example). If the Postgres volume already exists, "
            "also run: python scripts/python/scans/docker_clean.py --volumes --yes"
        )


def has_git_history() -> bool:
    return (REPO_ROOT / ".git").exists()


def maven_modules() -> list[str]:
    """Return backend Maven module directory names from the root POM."""
    require_file(ROOT_POM_FILE, "Root pom.xml")

    root = ET.parse(ROOT_POM_FILE).getroot()  # noqa: S314
    ns = {"m": root.tag.split("}")[0].strip("{")} if root.tag.startswith("{") else {}
    path = "./m:modules/m:module" if ns else "./modules/module"
    modules = []

    for element in root.findall(path, ns):
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
    if not script.is_file():
        raise RuntimeError(f"Internal error: script {script_name} does not exist in {script.parent}")
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
        FRONTEND_DIR / "build",  # vite.config.mjs sets outDir: "build"
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


def main_guard(main) -> None:  # type: ignore[no-untyped-def]
    """Uniform entry point: clean error line, non-zero exit, Ctrl+C friendly."""
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nInterrupted.")
        raise SystemExit(130)
    except RuntimeError as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
