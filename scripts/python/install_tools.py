#!/usr/bin/env python3
"""Install everything Catalogix needs to be bootstrapped from a bare Linux machine.

Run it through scripts/bash/install-all.sh, which first makes sure Python exists (the only
thing a shell script has to install) and then calls this file. This file uses only the Python
standard library, so it runs before anything else is installed.

What it installs
    aws        AWS CLI v2 (the official installer from AWS, not the distro package)
    terraform  Terraform (official zip from HashiCorp, checked against its published SHA-256)
    ansible    Ansible and boto3 in a virtualenv at <repo>/.venv, plus the Galaxy collections in
               infra/ansible/requirements.yaml. The other project scripts use this virtualenv
               automatically (see utils/venv.py), so there is nothing to activate.
    jq         from the system package manager
    ssh        ssh-keygen, from the system package manager (needed by create-key-pair.sh)

Works on any Linux with apt, dnf, yum or zypper: Ubuntu, Debian, Amazon Linux, RHEL and its
clones, Fedora, SUSE. Run it as a normal user (it uses sudo where needed) or as root (cloud VMs).
Safe to re-run: anything already installed is skipped.

Usage
    python3 scripts/python/install_tools.py                  # everything
    python3 scripts/python/install_tools.py --only terraform,aws
    python3 scripts/python/install_tools.py --terraform-version 1.12.2
"""

import argparse
import hashlib
import json
import os
import platform
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request
import venv
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
VENV_DIR = REPO_ROOT / ".venv"
ANSIBLE_REQUIREMENTS = REPO_ROOT / "infra" / "ansible" / "requirements.yaml"

# Used only if HashiCorp's "latest version" lookup is unreachable. The Terraform code in this
# repo needs >= 1.12.0.
TERRAFORM_FALLBACK_VERSION = "1.12.2"
TERRAFORM_LATEST_URL = "https://checkpoint-api.hashicorp.com/v1/check/terraform"

ALL_TOOLS = ["jq", "ssh", "aws", "terraform", "ansible"]


# ---------------------------------------------------------------- small helpers

def info(msg):
    print(f"\n\033[1;34m==>\033[0m {msg}")


def ok(msg):
    print(f"    \033[1;32m✓\033[0m {msg}")


def skip(msg):
    print(f"    \033[1;33m·\033[0m {msg} (already installed, skipping)")


def fail(msg):
    print(f"\n\033[1;31mERROR:\033[0m {msg}", file=sys.stderr)
    sys.exit(1)


def run(cmd, **kwargs):
    """Run a command, stop with a readable message if it fails."""
    try:
        return subprocess.run(cmd, check=True, **kwargs)
    except FileNotFoundError:
        fail(f"Command not found: {cmd[0]}")
    except subprocess.CalledProcessError as exc:
        fail(f"Command failed (exit {exc.returncode}): {' '.join(str(c) for c in cmd)}")


def output(cmd):
    """Return a command's stdout, or '' if it cannot run."""
    try:
        return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def sudo():
    """Command prefix for steps that need root: nothing if we are root, else sudo."""
    if os.geteuid() == 0:
        return []
    if shutil.which("sudo"):
        return ["sudo"]
    fail("This step needs root. Re-run as root, or install sudo.")


def machine():
    """Return (aws_name, hashicorp_name) for this CPU: x86_64/amd64 or aarch64/arm64."""
    arch = platform.machine().lower()
    if arch in ("x86_64", "amd64"):
        return "x86_64", "amd64"
    if arch in ("aarch64", "arm64"):
        return "aarch64", "arm64"
    fail(f"Unsupported CPU architecture: {arch}")


def download(url, dest, attempts=3):
    """Download a URL to a file, retrying a couple of times on network errors."""
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(url, timeout=60) as response, open(dest, "wb") as out:
                shutil.copyfileobj(response, out)
            return
        except OSError as exc:
            if attempt == attempts:
                fail(f"Could not download {url}: {exc}\n"
                     "       Check the machine's internet access and any proxy settings.")
            time.sleep(2 * attempt)


def unzip(zip_path, target_dir):
    """Extract a zip and keep file permissions (zipfile.extractall drops the executable bit)."""
    with zipfile.ZipFile(zip_path) as archive:
        for member in archive.infolist():
            extracted = archive.extract(member, target_dir)
            mode = member.external_attr >> 16
            if mode:
                os.chmod(extracted, stat.S_IMODE(mode))


# ---------------------------------------------------------------- system packages

def package_manager():
    for name in ("apt-get", "dnf", "yum", "zypper"):
        if shutil.which(name):
            return name
    fail("No supported package manager found (need apt-get, dnf, yum or zypper).")


# Package names differ between distro families.
PACKAGE_NAMES = {
    "jq": {"apt-get": "jq", "dnf": "jq", "yum": "jq", "zypper": "jq"},
    "ssh": {"apt-get": "openssh-client", "dnf": "openssh-clients",
            "yum": "openssh-clients", "zypper": "openssh-clients"},
}


def install_package(tool):
    manager = package_manager()
    name = PACKAGE_NAMES[tool][manager]
    if manager == "apt-get":
        run(sudo() + ["apt-get", "update", "-qq"])
        run(sudo() + ["apt-get", "install", "-y", "-qq", name])
    elif manager == "zypper":
        run(sudo() + ["zypper", "--non-interactive", "install", name])
    else:
        run(sudo() + [manager, "install", "-y", "-q", name])


def install_jq():
    info("jq")
    if shutil.which("jq"):
        return skip(output(["jq", "--version"]))
    install_package("jq")
    ok(output(["jq", "--version"]))


def install_ssh():
    info("ssh-keygen (OpenSSH client)")
    if shutil.which("ssh-keygen"):
        return skip("ssh-keygen")
    install_package("ssh")
    ok("ssh-keygen installed")


# ---------------------------------------------------------------- AWS CLI

def install_awscli():
    info("AWS CLI v2")
    if shutil.which("aws") and "aws-cli/2" in output(["aws", "--version"]):
        return skip(output(["aws", "--version"]))

    aws_arch, _ = machine()
    with tempfile.TemporaryDirectory() as tmp:
        zip_path = Path(tmp) / "awscliv2.zip"
        download(f"https://awscli.amazonaws.com/awscli-exe-linux-{aws_arch}.zip", zip_path)
        unzip(zip_path, tmp)
        # --update makes a re-run safe; default install location is /usr/local/aws-cli
        run(sudo() + [str(Path(tmp) / "aws" / "install"), "--update"], stdout=subprocess.DEVNULL)
    ok(output(["aws", "--version"]) or "AWS CLI installed (open a new shell if `aws` is not found)")


# ---------------------------------------------------------------- Terraform

def latest_terraform_version():
    try:
        with urllib.request.urlopen(TERRAFORM_LATEST_URL, timeout=15) as response:
            return json.load(response)["current_version"]
    except (OSError, KeyError, ValueError):
        return TERRAFORM_FALLBACK_VERSION


def install_terraform(version=None):
    info("Terraform")
    if shutil.which("terraform"):
        return skip(output(["terraform", "version"]).splitlines()[0])

    _, hashi_arch = machine()
    version = version or latest_terraform_version()
    base = f"https://releases.hashicorp.com/terraform/{version}"
    zip_name = f"terraform_{version}_linux_{hashi_arch}.zip"

    with tempfile.TemporaryDirectory() as tmp:
        zip_path = Path(tmp) / zip_name
        sums_path = Path(tmp) / "SHA256SUMS"
        download(f"{base}/{zip_name}", zip_path)
        download(f"{base}/terraform_{version}_SHA256SUMS", sums_path)

        # Refuse to install a file whose checksum does not match HashiCorp's published list.
        expected = None
        for line in sums_path.read_text().splitlines():
            digest, _, name = line.partition("  ")
            if name.strip() == zip_name:
                expected = digest
        actual = hashlib.sha256(zip_path.read_bytes()).hexdigest()
        if expected != actual:
            fail(f"Terraform download failed its SHA-256 check (expected {expected}, got {actual}).")

        unzip(zip_path, tmp)
        run(sudo() + ["install", "-m", "0755", str(Path(tmp) / "terraform"), "/usr/local/bin/terraform"])
    ok(output(["terraform", "version"]).splitlines()[0])


# ---------------------------------------------------------------- Ansible + boto3 (virtualenv)

def install_ansible():
    info(f"Ansible and boto3 in a virtualenv ({VENV_DIR})")
    python = VENV_DIR / "bin" / "python"

    if not python.exists():
        try:
            venv.EnvBuilder(with_pip=True).create(VENV_DIR)
        except Exception as exc:  # ensurepip missing on Debian/Ubuntu without python3-venv
            shutil.rmtree(VENV_DIR, ignore_errors=True)
            fail(f"Could not create a virtualenv: {exc}\n"
                 "       On Debian/Ubuntu run: sudo apt-get install -y python3-venv\n"
                 "       (scripts/bash/install-python.sh does this for you.)")

    if (VENV_DIR / "bin" / "ansible-playbook").exists():
        skip(output([str(VENV_DIR / "bin" / "ansible"), "--version"]).splitlines()[0])
    else:
        run([str(python), "-m", "pip", "install", "--quiet", "--upgrade", "pip"])
        # `ansible` is the full package (includes the collections the roles use);
        # boto3/botocore are what the amazon.aws inventory plugin and our scripts need.
        run([str(python), "-m", "pip", "install", "--quiet", "ansible", "boto3", "botocore"])
        ok(output([str(VENV_DIR / "bin" / "ansible"), "--version"]).splitlines()[0])

    # community.docker -> sonarqube role; amazon.aws -> dynamic EC2 inventory
    if ANSIBLE_REQUIREMENTS.is_file():
        info("Ansible Galaxy collections")
        run([str(VENV_DIR / "bin" / "ansible-galaxy"), "collection", "install",
             "-r", str(ANSIBLE_REQUIREMENTS)])
        ok(f"Collections installed from {ANSIBLE_REQUIREMENTS.relative_to(REPO_ROOT)}")


# ---------------------------------------------------------------- main

INSTALLERS = {
    "jq": install_jq,
    "ssh": install_ssh,
    "aws": install_awscli,
    "terraform": install_terraform,
    "ansible": install_ansible,
}


def main():
    parser = argparse.ArgumentParser(description="Install the tools Catalogix needs (stdlib only).")
    parser.add_argument("--only", help=f"Comma-separated subset of: {', '.join(ALL_TOOLS)}")
    parser.add_argument("--terraform-version", help="Install this Terraform version instead of the latest.")
    args = parser.parse_args()

    if sys.platform != "linux":
        fail("This installer is for Linux. On Windows use WSL (Ubuntu); on macOS install the tools with Homebrew.")

    tools = ALL_TOOLS
    if args.only:
        tools = [name.strip() for name in args.only.split(",") if name.strip()]
        unknown = [name for name in tools if name not in INSTALLERS]
        if unknown:
            fail(f"Unknown tool(s): {', '.join(unknown)}. Choose from: {', '.join(ALL_TOOLS)}")

    for name in tools:
        if name == "terraform":
            install_terraform(args.terraform_version)
        else:
            INSTALLERS[name]()

    info("Done")
    if "ansible" in tools:
        print(f"    Ansible lives in {VENV_DIR}. The scripts in scripts/python use it automatically;")
        print(f"    to run ansible by hand: source {VENV_DIR}/bin/activate")
    print("    Next: aws configure")


if __name__ == "__main__":
    main()
