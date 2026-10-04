#!/usr/bin/env python3
"""Run SonarQube analysis using the SonarScanner CLI Docker image."""

from __future__ import annotations

import argparse
import os
import socket
import time
import urllib.error
import urllib.request

from common import REPO_ROOT, require_command, run, run_python


DEFAULT_SCANNER_IMAGE = os.getenv(
    "SONAR_SCANNER_IMAGE",
    "sonarsource/sonar-scanner-cli:12.1",
)
DEFAULT_SONARQUBE_CONTAINER = os.getenv(
    "SONARQUBE_CONTAINER",
    "catalogix-sonarqube",
)
DEFAULT_SONARQUBE_IMAGE = os.getenv(
    "SONARQUBE_IMAGE",
    "sonarqube:community",
)
DEFAULT_SONARQUBE_NETWORK = os.getenv(
    "SONAR_NETWORK",
    "catalogix-sonar-net",
)


def wait_for_http(url: str, timeout_seconds: int = 180) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as response:
                if 200 <= response.status < 500:
                    return
        except (urllib.error.URLError, OSError):
            time.sleep(3)

    raise RuntimeError(f"SonarQube was not reachable at {url}.")


def start_managed_server(container: str, image: str, network: str, port: int) -> None:
    inspect = run(
        ["docker", "inspect", container],
        capture=True,
        check=False,
    )

    if inspect.returncode == 0:
        state = run(
            ["docker", "inspect", "--format", "{{.State.Status}}", container],
            capture=True,
            check=False,
        ).stdout.strip()

        if state != "running":
            run(["docker", "start", container])

        network_check = run(
            ["docker", "network", "inspect", network],
            capture=True,
            check=False,
        )
        if network_check.returncode != 0:
            run(["docker", "network", "create", network])

        run(
            ["docker", "network", "connect", network, container],
            check=False,
        )
        return

    run(
        ["docker", "network", "create", network],
        check=False,
    )
    run(
        [
            "docker",
            "run",
            "-d",
            "--name",
            container,
            "--network",
            network,
            "-p",
            f"{port}:9000",
            image,
        ]
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run Catalogix SonarQube analysis with Docker."
    )
    parser.add_argument(
        "--build",
        action="store_true",
        help="Run mvn clean verify before scanning.",
    )
    parser.add_argument(
        "--start-server",
        action="store_true",
        help="Start/reuse a local SonarQube Community container on localhost:9000.",
    )
    parser.add_argument(
        "--keep-server",
        action="store_true",
        help="Keep the managed SonarQube container running after the scan.",
    )
    parser.add_argument(
        "--scanner-image",
        default=DEFAULT_SCANNER_IMAGE,
        help=f"SonarScanner CLI image (default: {DEFAULT_SCANNER_IMAGE}).",
    )
    return parser.parse_args()


def main() -> int:
    require_command("docker")
    args = parse_args()

    token = os.getenv("SONAR_TOKEN", "").strip()
    if not token:
        raise RuntimeError(
            "SONAR_TOKEN is required. In PowerShell:\n"
            "  $env:SONAR_TOKEN='YOUR_LOCAL_SONARQUBE_TOKEN'"
        )

    managed_server = args.start_server
    server_host = "host.docker.internal"
    server_port = 9000
    scanner_network: str | None = None

    if managed_server:
        start_managed_server(
            DEFAULT_SONARQUBE_CONTAINER,
            DEFAULT_SONARQUBE_IMAGE,
            DEFAULT_SONARQUBE_NETWORK,
            server_port,
        )
        wait_for_http("http://127.0.0.1:9000/api/system/status")
        server_host = "sonarqube"
        scanner_network = DEFAULT_SONARQUBE_NETWORK
    else:
        host_url = os.getenv("SONAR_HOST_URL", "http://localhost:9000")
        server_host = host_url.replace("http://", "").replace("https://", "").rstrip("/")
        if ":" in server_host:
            host_only = server_host.rsplit(":", 1)[0]
            port_text = server_host.rsplit(":", 1)[1]
            if port_text.isdigit():
                server_port = int(port_text)
                server_host = host_only
        server_url = f"{host_url.rstrip('/')}/api/system/status"
        wait_for_http(server_url)

    if args.build:
        run_python("mvn.py", "clean-verify", "--all")

    command = [
        "docker",
        "run",
        "--rm",
    ]

    if scanner_network:
        command += ["--network", scanner_network]

    command += [
        "-v",
        f"{REPO_ROOT}:/usr/src",
        "-w",
        "/usr/src",
        "-e",
        f"SONAR_TOKEN={token}",
        args.scanner_image,
        f"-Dsonar.host.url=http://{server_host}:{server_port}",
        "-Dsonar.qualitygate.wait=true",
        "-Dsonar.projectKey=catalogix",
    ]

    run(command, cwd=REPO_ROOT)

    print("\nSonarQube analysis PASSED.")
    print("Dashboard: http://localhost:9000/dashboard?id=catalogix")

    if managed_server and not args.keep_server:
        run(["docker", "stop", DEFAULT_SONARQUBE_CONTAINER], check=False)

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
