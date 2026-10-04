#!/usr/bin/env python3
"""Run lightweight end-to-end smoke tests against the Catalogix gateway."""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request
import uuid


def request(
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    body: object | None = None,
    token: str | None = None,
    timeout: int = 15,
) -> tuple[int, str, dict[str, str]]:
    url = base_url.rstrip("/") + path
    payload = None

    headers = {
        "Accept": "application/json, text/plain, */*",
    }

    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(
        url,
        data=payload,
        headers=headers,
        method=method,
    )

    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return (
                response.status,
                response.read().decode("utf-8", errors="replace"),
                dict(response.headers.items()),
            )
    except urllib.error.HTTPError as exc:
        return (
            exc.code,
            exc.read().decode("utf-8", errors="replace"),
            dict(exc.headers.items()),
        )


def wait_for_gateway(base_url: str, timeout_seconds: int) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        status, _, _ = request(base_url, "/gateway/health", timeout=5)
        if status == 200:
            return
        time.sleep(2)
    raise RuntimeError(
        f"Gateway did not become healthy within {timeout_seconds} seconds."
    )


def assert_status(name: str, status: int, expected: set[int]) -> None:
    if status not in expected:
        raise RuntimeError(
            f"{name} failed: HTTP {status}; expected one of {sorted(expected)}."
        )
    print(f"[PASS] {name} -> HTTP {status}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run Catalogix gateway smoke tests."
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("CATALOGIX_BASE_URL", "http://localhost:11000"),
    )
    parser.add_argument(
        "--wait",
        type=int,
        default=120,
        help="Seconds to wait for /gateway/health.",
    )
    parser.add_argument(
        "--email",
        default=os.getenv("SMOKE_EMAIL"),
        help="Existing local test-user email for authenticated checks.",
    )
    parser.add_argument(
        "--password",
        default=os.getenv("SMOKE_PASSWORD"),
        help="Existing local test-user password for authenticated checks.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    print(f"Smoke testing {args.base_url}")
    wait_for_gateway(args.base_url, args.wait)

    status, body, _ = request(args.base_url, "/gateway/health")
    assert_status("Gateway health", status, {200})
    if "OK" not in body:
        raise RuntimeError("Gateway health returned 200 but did not contain 'OK'.")

    status, body, _ = request(args.base_url, "/")
    assert_status("Frontend", status, {200})
    if not body.strip():
        raise RuntimeError("Frontend returned HTTP 200 with an empty body.")

    # Catalog access is authentication-protected in the current security model.
    # Accept 401/403 here when no credentials are supplied; with credentials,
    # require a successful catalog response.
    status, _, _ = request(
        args.base_url,
        "/api/products?page=0&size=1",
    )
    if args.email and args.password:
        raise RuntimeError(
            "Authenticated smoke flow requires the login step below; "
            "unexpected anonymous path."
        )

    if args.email and args.password:
        login_body = {
            "email": args.email,
            "password": args.password,
        }
        status, body, _ = request(
            args.base_url,
            "/api/users/login",
            method="POST",
            body=login_body,
        )
        assert_status("Login", status, {200})

        try:
            token = json.loads(body)["accessToken"]
        except (KeyError, TypeError, json.JSONDecodeError) as exc:
            raise RuntimeError("Login response did not contain accessToken.") from exc

        status, _, _ = request(
            args.base_url,
            "/api/products?page=0&size=1",
            token=token,
        )
        assert_status("Authenticated catalog listing", status, {200})

        status, _, _ = request(
            args.base_url,
            "/api/cart",
            token=token,
        )
        assert_status("Authenticated cart", status, {200})
    else:
        if status in {401, 403, 200}:
            print(
                f"[PASS] Catalog endpoint reachable without credentials -> HTTP {status}"
            )
        else:
            raise RuntimeError(
                f"Catalog endpoint returned unexpected HTTP {status}."
            )

    print("\nCatalogix smoke tests PASSED.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"\nERROR: {exc}")
        raise SystemExit(1)
