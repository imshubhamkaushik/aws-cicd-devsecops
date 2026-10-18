#!/usr/bin/env python3
"""Create the local SSH key pair used for the Catalogix bootstrap EC2 instances."""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--path",
        type=Path,
        default=Path.home() / ".ssh" / "catalogix-key",
        help="Private key path (default: ~/.ssh/catalogix-key).",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Replace an existing key pair without an interactive confirmation.",
    )
    args = parser.parse_args(argv)

    ssh_keygen = shutil.which("ssh-keygen")
    if not ssh_keygen:
        print("ERROR: ssh-keygen was not found. Install OpenSSH with install_tools.py first.", file=sys.stderr)
        return 1

    key_path = args.path.expanduser().resolve()
    public_path = Path(f"{key_path}.pub")
    key_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        key_path.parent.chmod(0o700)
    except OSError:
        # chmod semantics vary on Windows/WSL-mounted filesystems; creation must still be reported.
        pass

    if key_path.exists() or public_path.exists():
        print(f"An SSH key file already exists at {key_path} or {public_path}.")
        if not args.force:
            if not sys.stdin.isatty():
                print("Refusing to replace it non-interactively. Re-run with --force if rotation is intended.", file=sys.stderr)
                return 2
            answer = input("Replacing it can break access to existing EC2 instances. Type 'yes' to replace: ").strip()
            if answer != "yes":
                print("Existing key pair preserved; nothing changed.")
                return 0
        key_path.unlink(missing_ok=True)
        public_path.unlink(missing_ok=True)

    print(f"Generating an Ed25519 SSH key at {key_path} ...")
    result = subprocess.run(
        [ssh_keygen, "-t", "ed25519", "-f", str(key_path), "-C", "catalogix-bootstrap", "-N", ""],
        text=True,
        check=False,
    )
    if result.returncode != 0:
        print(f"ERROR: ssh-keygen failed with exit code {result.returncode}.", file=sys.stderr)
        return result.returncode or 1

    try:
        key_path.chmod(0o400)
        public_path.chmod(0o644)
    except OSError as exc:
        print(f"WARNING: could not fully set key permissions: {exc}", file=sys.stderr)

    print(f"Private key: {key_path} (target mode 400; keep private)")
    print(f"Public key:  {public_path} (target mode 644; this is the key Terraform receives)")
    print("\nNext steps:")
    print(f"  - Optional: set public_key = file(\"{public_path}\") in infra/terraform/bootstrap-infra/terraform.tfvars.")
    if key_path == (Path.home() / ".ssh" / "catalogix-key").resolve():
        print("  - The bootstrap uses ~/.ssh/catalogix-key.pub by default.")
    else:
        print(f'  - For a custom path, set public_key = file("{public_path}") in infra/terraform/bootstrap-infra/terraform.tfvars.')
    print("  - Do not replace this key after provisioning unless you also plan to update server access.")
    print("  - For multiple operators, use individual keys or AWS Systems Manager Session Manager.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
