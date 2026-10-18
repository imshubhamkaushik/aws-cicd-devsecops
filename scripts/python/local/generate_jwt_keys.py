#!/usr/bin/env python3
"""Generate local RS256 key pairs and update the git-ignored .env file.

The user-token pair signs application access tokens. The internal pair authenticates service-to-
service calls. Rotating the keys invalidates tokens issued with the old keys.
"""
from __future__ import annotations

import argparse
import base64
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
ENV_FILE = REPO_ROOT / ".env"
ENV_EXAMPLE = REPO_ROOT / ".env.example"
LEGACY_KEYS = {"JWT_SECRET", "JWT_SECRET_PREVIOUS"}
KEY_NAMES = {
    "JWT_PRIVATE_KEY_B64",
    "JWT_PUBLIC_KEY_B64",
    "INTERNAL_JWT_PRIVATE_KEY_B64",
    "INTERNAL_JWT_PUBLIC_KEY_B64",
}


def run_openssl(args: list[str]) -> bytes:
    result = subprocess.run(
        ["openssl", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"openssl {' '.join(args[:2])} failed: {detail}")
    return result.stdout


def b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def generate_pair(directory: Path, name: str, bits: int) -> tuple[str, str]:
    private_pem = directory / f"{name}-private.pem"
    public_pem = directory / f"{name}-public.pem"
    run_openssl(["genpkey", "-algorithm", "RSA", "-pkeyopt", f"rsa_keygen_bits:{bits}", "-out", str(private_pem)])
    run_openssl(["pkey", "-in", str(private_pem), "-pubout", "-out", str(public_pem)])
    private_der = run_openssl([
        "pkcs8", "-topk8", "-nocrypt", "-in", str(private_pem), "-outform", "DER"
    ])
    public_der = run_openssl(["pkey", "-pubin", "-in", str(public_pem), "-outform", "DER"])
    return b64(private_der), b64(public_der)


def read_env_key_names(path: Path) -> set[str]:
    found: set[str] = set()
    if not path.is_file():
        return found
    for line in path.read_text(encoding="utf-8").splitlines():
        trimmed = line.strip()
        if trimmed and not trimmed.startswith("#") and "=" in trimmed:
            found.add(trimmed.split("=", 1)[0].strip())
    return found


def update_env(path: Path, updates: dict[str, str]) -> None:
    if not path.exists():
        if not ENV_EXAMPLE.is_file():
            raise RuntimeError(f"Neither {path} nor {ENV_EXAMPLE} exists.")
        shutil.copyfile(ENV_EXAMPLE, path)

    lines = path.read_text(encoding="utf-8").splitlines()
    result: list[str] = []
    written: set[str] = set()
    for line in lines:
        key = line.split("=", 1)[0].strip() if "=" in line else ""
        if key in LEGACY_KEYS or key.startswith("SERVICE_JWT_"):
            continue
        if key in updates:
            if key not in written:
                result.append(f"{key}={updates[key]}")
                written.add(key)
            continue
        result.append(line)
    for key, value in updates.items():
        if key not in written:
            result.append(f"{key}={value}")
    path.write_text("\n".join(result).rstrip() + "\n", encoding="utf-8")
    try:
        path.chmod(0o600)
    except OSError:
        pass


def apply_kubernetes_secret(namespace: str, updates: dict[str, str]) -> None:
    kubectl = shutil.which("kubectl")
    if not kubectl:
        raise RuntimeError("kubectl is required for --kubernetes.")
    command = [
        kubectl, "create", "secret", "generic", "catalogix-jwt-keys",
        "--namespace", namespace,
    ]
    key_map = {
        "JWT_PRIVATE_KEY_B64": "jwt_private_key_b64",
        "JWT_PUBLIC_KEY_B64": "jwt_public_key_b64",
        "INTERNAL_JWT_PRIVATE_KEY_B64": "internal_jwt_private_key_b64",
        "INTERNAL_JWT_PUBLIC_KEY_B64": "internal_jwt_public_key_b64",
    }
    with tempfile.TemporaryDirectory(prefix="catalogix-jwt-") as tmp:
        env_path = Path(tmp) / "jwt-keys-k8s.env"
        env_path.write_text(
            "".join(f"{key_map[key]}={value}\n" for key, value in updates.items()),
            encoding="utf-8",
        )
        env_path.chmod(0o600)
        command.extend(["--from-env-file", str(env_path), "--dry-run=client", "-o", "yaml"])
        rendered = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        if rendered.returncode != 0:
            raise RuntimeError(rendered.stderr.decode("utf-8", errors="replace").strip())
        namespace_yaml = subprocess.run(
            [kubectl, "create", "namespace", namespace, "--dry-run=client", "-o", "yaml"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if namespace_yaml.returncode != 0:
            raise RuntimeError(namespace_yaml.stderr.decode("utf-8", errors="replace").strip())
        namespace_apply = subprocess.run(
            [kubectl, "apply", "-f", "-"], input=namespace_yaml.stdout,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if namespace_apply.returncode != 0:
            raise RuntimeError(namespace_apply.stderr.decode("utf-8", errors="replace").strip())
        secret_apply = subprocess.run(
            [kubectl, "apply", "-f", "-"], input=rendered.stdout,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if secret_apply.returncode != 0:
            raise RuntimeError(secret_apply.stderr.decode("utf-8", errors="replace").strip())
        print(f"Created/updated Kubernetes Secret catalogix-jwt-keys in namespace {namespace}.")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--kubernetes", nargs="?", const="catalogix", metavar="NAMESPACE",
        help="also apply catalogix-jwt-keys to Kubernetes (default namespace: catalogix)",
    )
    parser.add_argument(
        "--force", action="store_true",
        help="rotate existing signing keys without an interactive confirmation",
    )
    args = parser.parse_args(argv)

    if not shutil.which("openssl"):
        print("ERROR: openssl is required.", file=sys.stderr)
        return 1
    if args.kubernetes and not shutil.which("kubectl"):
        print("ERROR: kubectl is required for --kubernetes.", file=sys.stderr)
        return 1

    existing = read_env_key_names(ENV_FILE)
    if existing & KEY_NAMES and not args.force:
        warning = (
            "Existing JWT key values were found in .env. Rotating them invalidates already-issued "
            "tokens and may require updating running workloads."
        )
        print(warning)
        if not sys.stdin.isatty():
            print("Re-run with --force if key rotation is intentional.", file=sys.stderr)
            return 2
        if input("Type 'yes' to rotate the keys: ").strip() != "yes":
            print("Key generation cancelled; .env was not changed.")
            return 0

    try:
        with tempfile.TemporaryDirectory(prefix="catalogix-jwt-keygen-") as temp:
            temp_dir = Path(temp)
            os.chmod(temp_dir, 0o700)
            user_private, user_public = generate_pair(temp_dir, "user", 3072)
            internal_private, internal_public = generate_pair(temp_dir, "internal", 2048)
            updates = {
                "JWT_PRIVATE_KEY_B64": user_private,
                "JWT_PUBLIC_KEY_B64": user_public,
                "INTERNAL_JWT_PRIVATE_KEY_B64": internal_private,
                "INTERNAL_JWT_PUBLIC_KEY_B64": internal_public,
            }
            # Apply to Kubernetes first when requested; if this fails, local .env is left untouched.
            if args.kubernetes:
                apply_kubernetes_secret(args.kubernetes, updates)
            update_env(ENV_FILE, updates)
    except (OSError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(f"Generated user and internal RS256 key pairs and updated {ENV_FILE} (target mode 600).")
    print("Only user-svc receives JWT_PRIVATE_KEY_B64; services receive the configured public/internal values.")
    if args.kubernetes:
        print("Kubernetes was updated before .env so a kubectl error cannot silently rotate only the local keys.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
