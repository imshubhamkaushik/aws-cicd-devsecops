#!/usr/bin/env python3
"""Smoke tests through the gateway, for Docker Compose and for local Kubernetes.

Both local targets expose the same gateway on http://localhost:11000 and seed the same admin
(admin@catalogix.local / AdminAdminAdmin), so the same checks run against either.
Override with --base-url / --email / --password (or CATALOGIX_BASE_URL, SMOKE_EMAIL, SMOKE_PASSWORD).
"""

from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request

from common import main_guard

DEFAULT_EMAIL = "admin@catalogix.local"
DEFAULT_PASSWORD = "AdminAdminAdmin"  # noqa: S105 - documented local seed admin, see docker-compose.yaml


def request(base_url: str, path: str, *, method: str = "GET", body: object | None = None,
            token: str | None = None, timeout: int = 15) -> tuple[int, str]:
    headers = {"Accept": "application/json, text/plain, */*"}
    payload = None
    if body is not None:
        payload = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(base_url.rstrip("/") + path, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310 - http(s) only
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")
    except OSError:
        return 0, ""


def wait_for(base_url: str, path: str, ok: set[int], timeout_seconds: int, label: str) -> None:
    deadline = time.time() + timeout_seconds
    status = 0;
    
    while time.time() < deadline:
        status, _ = request(base_url, path, timeout=5)
        
        if status in ok:
            return
        
        time.sleep(3)
    raise RuntimeError(
        f"{label} did not become ready within {timeout_seconds}s" 
        f"(last HTTP {status})."
    )


def check(name: str, status: int, expected: set[int]) -> None:
    if status not in expected:
        raise RuntimeError(f"{name} failed: HTTP {status}; expected one of {sorted(expected)}.")
    print(f"[PASS] {name} -> HTTP {status}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Catalogix gateway smoke tests.")
    parser.add_argument("--base-url", default=os.getenv("CATALOGIX_BASE_URL", "http://localhost:11000"))
    parser.add_argument("--wait", type=int, default=240, help="Seconds to wait for the stack (JVMs start slowly).")
    parser.add_argument("--email", default=os.getenv("SMOKE_EMAIL", DEFAULT_EMAIL))
    parser.add_argument("--password", default=os.getenv("SMOKE_PASSWORD", DEFAULT_PASSWORD))
    parser.add_argument("--anonymous-only", action="store_true", help="Skip the login flow.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    base = args.base_url
    print(f"Smoke testing {base}")

    wait_for(base, "/gateway/health", {200}, args.wait, "Gateway")
    status, body = request(base, "/gateway/health")
    check("Gateway health", status, {200})
    if "OK" not in body:
        raise RuntimeError("Gateway health returned 200 without 'OK'.")

    status, body = request(base, "/")
    check("Frontend served", status, {200})
    if "<div" not in body.lower():
        raise RuntimeError("Frontend returned 200 but no HTML app shell.")

    # Protected route must refuse anonymous callers (proves the auth filter is active end to end).
    status, _ = request(base, "/api/cart")
    check("Anonymous cart is rejected", status, {401, 403})

    # Unknown API paths must not fall through to the SPA.
    status, _ = request(base, "/api/does-not-exist")
    check("Unknown /api path -> 404", status, {404})

    if args.anonymous_only:
        print("\nCatalogix smoke tests PASSED (anonymous only).")
        return 0

    # user-svc is the slowest to become ready (Flyway + RabbitMQ + admin seeding): poll the real login.
    deadline = time.time() + args.wait
    status, body = 0, ""
    while time.time() < deadline:
        status, body = request(base, "/api/users/login", method="POST",
                               body={"email": args.email, "password": args.password})
        if status == 200:
            break
        if status in (400, 401, 403, 423, 429):
            break  # service is up and answering: wrong credentials, locked or throttled -> do not hammer
        time.sleep(4)
    check("Login as seeded admin", status, {200})
    try:
        token = json.loads(body)["accessToken"]
    except (KeyError, TypeError, json.JSONDecodeError) as exc:
        raise RuntimeError("Login response did not contain accessToken.") from exc

    status, body = request(base, "/api/products?page=0&size=5", token=token)
    check("Catalog listing (catalog-svc -> inventory-svc)", status, {200})
    status, _ = request(base, "/api/cart", token=token)
    check("Cart (cart-svc)", status, {200})
    status, _ = request(base, "/api/users/me", token=token)
    check("Profile (user-svc)", status, {200})
    status, _ = request(base, "/api/orders", token=token)
    check("Orders (checkout-svc)", status, {200})

    print("\nCatalogix smoke tests PASSED.")
    return 0


if __name__ == "__main__":
    main_guard(main)
