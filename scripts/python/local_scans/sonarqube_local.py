#!/usr/bin/env python3
"""Run the SonarQube analysis locally (same scanner image and properties file as Jenkins).

Zero-setup mode (recommended):
    python sonarqube_local.py --start-server --build

  * starts a local SonarQube Community container (data kept in Docker volumes),
  * waits until it reports status UP,
  * sets the admin password on first use and generates a short-lived analysis token itself,
  * runs `mvn clean verify` + frontend coverage (--build) so JaCoCo/lcov reports exist,
  * runs the scanner and waits for the quality gate.

Use your own server instead:
    SONAR_HOST_URL=http://localhost:9000 SONAR_TOKEN=<token> python sonarqube_local.py

The scanner version comes from ansible/group_vars/all/vars.yaml (same as Jenkins).
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

from common import (
    BACKEND_DIR,
    FRONTEND_DIR,
    REPO_MOUNT,
    REPO_ROOT,
    main_guard,
    require_docker,
    run,
    run_python,
    tool_version,
)

CONTAINER = os.getenv("SONARQUBE_CONTAINER", "catalogix-sonarqube")
SERVER_IMAGE = os.getenv("SONARQUBE_IMAGE", "sonarqube:community")
NETWORK = os.getenv("SONAR_NETWORK", "catalogix-sonar-net")
VOLUMES = ("catalogix-sonar-data", "catalogix-sonar-extensions", "catalogix-sonar-logs")
PROJECT_KEY = "catalogix"
# Local-only throw-away server. Override with SONAR_ADMIN_PASSWORD. SonarQube rejects the
# default "admin" password for API use until it is changed, so the script changes it once.
ADMIN_PASSWORD = os.getenv("SONAR_ADMIN_PASSWORD", "Catalogix-Local-1!")  # NOSONAR
TOKEN_NAME = "catalogix-local-scan"


# ---------------------------------------------------------------- HTTP helpers
def _call(base: str, path: str, *, method: str = "GET", data: dict | None = None,
          auth: tuple[str, str] | None = None, timeout: int = 10) -> tuple[int, str]:
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    req = urllib.request.Request(base.rstrip("/") + path, data=body, method=method)
    if auth:
        raw = base64.b64encode(f"{auth[0]}:{auth[1]}".encode()).decode()
        req.add_header("Authorization", f"Basic {raw}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # NOSONAR
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode(errors="replace")
    except OSError:
        return 0, ""


def wait_until_up(base: str, timeout_seconds: int = 300) -> None:
    """/api/system/status answers 200 while still STARTING, so the JSON status must be checked."""
    print(f"Waiting for SonarQube at {base} (first start takes 1-2 minutes)...", flush=True)
    deadline = time.time() + timeout_seconds
    status = "unreachable"
    while time.time() < deadline:
        code, body = _call(base, "/api/system/status")
        if code == 200:
            try:
                status = json.loads(body).get("status", "unknown")
            except json.JSONDecodeError:
                status = "unknown"
            if status == "UP":
                return
        time.sleep(4)
    raise RuntimeError(f"SonarQube did not reach status UP within {timeout_seconds}s (last: {status}). "
                       f"Check: docker logs {CONTAINER}")


def ensure_admin_and_token(base: str) -> str:
    """Return a fresh analysis token for the local server, bootstrapping the admin password if needed."""
    admin = ("admin", ADMIN_PASSWORD)
    _, body = _call(base, "/api/authentication/validate", auth=admin)
    valid = '"valid":true' in body.replace(" ", "")
    if not valid:
        code, _ = _call(
            base, "/api/users/change_password", method="POST", auth=("admin", "admin"),
            data={"login": "admin", "previousPassword": "admin", "password": ADMIN_PASSWORD},
        )
        if code not in (200, 204):
            raise RuntimeError(
                "Could not log in to the local SonarQube as admin. If you changed the admin password in the "
                "UI, set SONAR_ADMIN_PASSWORD to it, or provide SONAR_TOKEN yourself."
            )
    _call(base, "/api/user_tokens/revoke", method="POST", auth=admin, data={"name": TOKEN_NAME})
    code, body = _call(base, "/api/user_tokens/generate", method="POST", auth=admin, data={"name": TOKEN_NAME})
    if code != 200:
        raise RuntimeError(f"Token generation failed (HTTP {code}): {body[:200]}")
    return json.loads(body)["token"]


# ------------------------------------------------------------ managed server
def start_managed_server() -> None:
    run(["docker", "network", "create", NETWORK], check=False, capture=True, quiet=True)
    exists = run(["docker", "inspect", CONTAINER], check=False, capture=True, quiet=True).returncode == 0
    if exists:
        run(["docker", "start", CONTAINER], check=False, capture=True)
        run(["docker", "network", "connect", NETWORK, CONTAINER], check=False, capture=True, quiet=True)
        return
    data, ext, logs = VOLUMES
    run([
        "docker", "run", "-d", "--name", CONTAINER, "--network", NETWORK,
        "-p", "9000:9000",
        "-v", f"{data}:/opt/sonarqube/data",
        "-v", f"{ext}:/opt/sonarqube/extensions",
        "-v", f"{logs}:/opt/sonarqube/logs",
        "-e", "SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true",  # no vm.max_map_count tuning needed locally
        SERVER_IMAGE,
    ])


# ------------------------------------------------------------------ inputs
def ensure_reports(build: bool) -> None:
    if build:
        run_python("mvn.py", "clean-verify", "--all")
        run_python("npm.py", "coverage")
        return
    classes = list(BACKEND_DIR.glob("*/target/classes"))
    if not classes:
        raise RuntimeError(
            "No compiled classes found (backend/*/target/classes). The Java analysis needs them.\n"
            "Re-run with --build, or run: python scripts/python/scans/mvn.py clean-verify --all"
        )
    if not list(BACKEND_DIR.glob("*/target/site/jacoco/jacoco.xml")):
        print("WARNING: no JaCoCo reports found; backend coverage will show as 0%. Use --build.")
    if not (FRONTEND_DIR / "coverage" / "lcov.info").is_file():
        print("WARNING: frontend/coverage/lcov.info not found; frontend coverage will show as 0%. Use --build.")


def scanner_target(host_url: str, managed: bool) -> tuple[str, list[str]]:
    """URL the *scanner container* must use, plus extra docker-run args.

    Inside a container 'localhost' is the container itself, so a host-side URL has to be
    rewritten to host.docker.internal (and mapped explicitly, which Linux needs).
    """
    if managed:
        return f"http://{CONTAINER}:9000", ["--network", NETWORK] # NOSONAR
    parsed = urllib.parse.urlparse(host_url)
    host = parsed.hostname or "localhost"
    extra: list[str] = []
    if host in ("localhost", "127.0.0.1", "::1"):
        host = "host.docker.internal"
        extra = ["--add-host", "host.docker.internal:host-gateway"]
    port = f":{parsed.port}" if parsed.port else ""
    return f"{parsed.scheme or 'http'}://{host}{port}{parsed.path.rstrip('/')}", extra


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Catalogix SonarQube analysis with Docker.")
    parser.add_argument("--build", action="store_true", help="Run mvn clean verify + frontend coverage first.")
    parser.add_argument("--start-server", action="store_true", help="Start/reuse a local SonarQube container on :9000.")
    parser.add_argument("--keep-server", action="store_true", help="Leave the managed server running afterwards.")
    parser.add_argument("--scanner-image", default=None, help="Override the SonarScanner CLI image.")
    return parser.parse_args()


def main() -> int:
    require_docker()
    args = parse_args()
    scanner_image = args.scanner_image or os.getenv("SONAR_SCANNER_IMAGE") or (
        f"sonarsource/sonar-scanner-cli:{tool_version('sonar_scanner_version')}"
    )

    host_url = os.getenv("SONAR_HOST_URL", "http://localhost:9000").rstrip("/") # NOSONAR
    token = os.getenv("SONAR_TOKEN", "").strip()
    managed = args.start_server

    if managed:
        host_url = "http://localhost:9000"
        start_managed_server()
    elif not token:
        raise RuntimeError(
            "SONAR_TOKEN is not set. Either:\n"
            "  - add --start-server to let this script run SonarQube and create the token, or\n"
            "  - export SONAR_TOKEN (PowerShell: $env:SONAR_TOKEN='...') for your own server."
        )

    wait_until_up(host_url)
    if managed and not token:
        token = ensure_admin_and_token(host_url)

    ensure_reports(args.build)

    scanner_url, extra_docker = scanner_target(host_url, managed)
    run([
        "docker", "run", "--rm", *extra_docker,
        "-v", REPO_MOUNT, "-w", "/repo",
        "-e", f"SONAR_TOKEN={token}",
        "-e", f"SONAR_HOST_URL={scanner_url}",
        scanner_image,
        # sonar-project.properties in the repo root supplies sources, tests, coverage paths.
        "-Dsonar.projectBaseDir=/repo",
        f"-Dsonar.projectKey={PROJECT_KEY}",
        "-Dsonar.qualitygate.wait=true",
        "-Dsonar.qualitygate.timeout=300",
    ], cwd=REPO_ROOT)

    print("\nSonarQube analysis and quality gate PASSED.")
    print(f"Dashboard: {host_url}/dashboard?id={PROJECT_KEY}")
    if managed:
        print(f"Local admin login: admin / {ADMIN_PASSWORD}")
        if not args.keep_server:
            run(["docker", "stop", CONTAINER], check=False, capture=True)
    return 0


if __name__ == "__main__":
    main_guard(main)
