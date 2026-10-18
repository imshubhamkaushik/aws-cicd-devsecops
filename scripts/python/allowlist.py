#!/usr/bin/env python3

"""
Additive operator IP allowlist for Jenkins (SSH 22, UI 8080) and the EKS API.

Why this exists
---------------
Terraform used to look up your public IP at apply time and allow only that
address, so an ISP change, a second operator, or a different network replaced
the old IP and locked the others out. This tool keeps a list instead:

  * the list lives in the Terraform state bucket (access/allowed-cidrs.json),
    so every machine you run the scripts from sees the same list;
  * `sync` adds your current IP only if it is not already there, and never
    removes anything;
  * entries stay until you remove or prune them, or until the deployment is
    destroyed (destroy_infra.py deletes the list);
  * the list is handed to Terraform through allowed_cidrs.auto.tfvars.json
    (gitignored), and bootstrap-infra turns it into one security group rule
    per CIDR, so adding an IP never replaces the existing rules.

Usage (from scripts/python):
    python3 scripts/python/allowlist.py list
    python3 scripts/python/allowlist.py sync                  # add my current IP if new
    python3 scripts/python/allowlist.py add 203.0.113.7/32 --label "office"
    python3 scripts/python/allowlist.py remove 203.0.113.7/32
    python3 scripts/python/allowlist.py prune --older-than 30
    python3 scripts/python/allowlist.py delete                # used by destroy_infra.py

After any change, apply it with: python3 scripts/python/bootstrap.py infra
(and re-run the platform-infra pipeline so the EKS API list is updated too).
"""

import argparse
import ipaddress
import json
import os
import sys
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from utils.venv import reexec_in_venv

if __name__ == "__main__":
    reexec_in_venv()

ROOT_DIR = Path(__file__).resolve().parents[2]
TFVARS_FILE = ROOT_DIR / "infra" / "terraform" / "bootstrap-infra" / "allowed_cidrs.auto.tfvars.json"

BUCKET = os.environ.get("TFSTATE_BUCKET", "catalogix-tfstate")
REGION = os.environ.get("AWS_REGION") or os.environ.get("AWS_DEFAULT_REGION") or "ap-south-1"
KEY = "access/allowed-cidrs.json"
IP_LOOKUP_URL = "https://checkip.amazonaws.com"

# EKS accepts at most 40 public_access_cidrs, and one of them is the Jenkins IP.
WARN_ABOVE = 30
MIN_PREFIX = 16  # refuse anything wider than a /16


class AllowlistError(Exception):
    """A problem the operator can fix; the message says how."""


class Conflict(AllowlistError):
    """Someone else changed the list between our read and our write."""


# ---------------------------------------------------------------------------
# Pure helpers
# ---------------------------------------------------------------------------

def normalize_cidr(text: str) -> str:
    """Return a canonical IPv4 CIDR ('203.0.113.7' becomes '203.0.113.7/32')."""

    raw = (text or "").strip()
    if not raw:
        raise AllowlistError("Empty address.")
    try:
        network = ipaddress.ip_network(raw, strict=True)
    except ValueError as exc:
        raise AllowlistError(f"'{raw}' is not a valid IPv4 address or CIDR ({exc}).") from exc
    if network.version != 4:
        raise AllowlistError("Only IPv4 is supported here.")
    if network.prefixlen < MIN_PREFIX:
        raise AllowlistError(
            f"{network} is wider than /{MIN_PREFIX}. Refusing to open Jenkins and SSH that widely."
        )
    return str(network)


def today() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def new_entry(cidr: str, label: str = "") -> dict:
    return {"cidr": cidr, "label": label, "added": today(), "last_seen": today()}


def add_entry(entries: list[dict], cidr: str, label: str = "", *, seen: bool = False):
    """Return (entries, changed). Existing entries are kept; only new CIDRs are appended.

    seen=True also refreshes last_seen of an existing entry (used when the
    entry is the IP the operator is connecting from right now).
    """

    updated = [dict(e) for e in entries]
    for entry in updated:
        if entry["cidr"] == cidr:
            if seen and entry.get("last_seen") != today():
                entry["last_seen"] = today()
                return updated, True
            return updated, False
    updated.append(new_entry(cidr, label))
    return updated, True


def remove_entry(entries: list[dict], cidr: str):
    updated = [dict(e) for e in entries if e["cidr"] != cidr]
    return updated, len(updated) != len(entries)


def stale_entries(entries: list[dict], *, older_than_days: int | None, keep: set[str],
                  now: date | None = None) -> list[dict]:
    """Entries to prune: not in `keep`, and unused for `older_than_days` (None means all of them)."""

    now = now or datetime.now(timezone.utc).date()
    result = []
    for entry in entries:
        if entry["cidr"] in keep:
            continue
        if older_than_days is None:
            result.append(entry)
            continue
        last = entry.get("last_seen") or entry.get("added") or "1970-01-01"
        try:
            last_date = date.fromisoformat(last)
        except ValueError:
            last_date = date.fromisoformat("1970-01-01")
        if now - last_date >= timedelta(days=older_than_days):
            result.append(entry)
    return result


def cidrs_of(entries: list[dict]) -> list[str]:
    return sorted({e["cidr"] for e in entries})


# ---------------------------------------------------------------------------
# Storage: one JSON object in the state bucket
# ---------------------------------------------------------------------------

class Store:
    """Reads and writes the list. Writes are conditional on the object's ETag,
    so two operators changing it at once cannot silently overwrite each other."""

    def __init__(self, client, bucket: str = BUCKET, key: str = KEY):
        self.client = client
        self.bucket = bucket
        self.key = key

    @staticmethod
    def _code(exc) -> str:
        response = getattr(exc, "response", None) or {}
        return str(response.get("Error", {}).get("Code", ""))

    def load(self) -> tuple[list[dict], str | None]:
        try:
            response = self.client.get_object(Bucket=self.bucket, Key=self.key)
        except Exception as exc:  # botocore ClientError, kept generic so tests need no boto3
            code = self._code(exc)
            if code in ("NoSuchKey", "404"):
                return [], None
            if code == "NoSuchBucket":
                raise AllowlistError(
                    f"State bucket '{self.bucket}' does not exist. Run: python3 scripts/python/bootstrap.py backend"
                ) from exc
            if code in ("AccessDenied", "403"):
                raise AllowlistError(
                    f"No access to s3://{self.bucket}/{self.key}. "
                    "Check the AWS profile (aws sts get-caller-identity)."
                ) from exc
            raise
        body = response["Body"].read()
        try:
            data = json.loads(body)
            entries = [dict(e) for e in data.get("entries", [])]
            for entry in entries:
                entry["cidr"] = normalize_cidr(entry["cidr"])
        except (ValueError, KeyError, TypeError, AttributeError) as exc:
            raise AllowlistError(
                f"s3://{self.bucket}/{self.key} is not valid JSON in the expected format ({exc})."
            ) from exc
        return entries, response.get("ETag")

    def save(self, entries: list[dict], etag: str | None) -> None:
        body = json.dumps({"version": 1, "entries": entries}, indent=2, sort_keys=True).encode()
        kwargs = dict(
            Bucket=self.bucket,
            Key=self.key,
            Body=body,
            ContentType="application/json",
            ServerSideEncryption="AES256",
        )
        # First write must not overwrite a list someone else just created;
        # later writes must be against the version we read.
        if etag:
            kwargs["IfMatch"] = etag
        else:
            kwargs["IfNoneMatch"] = "*"
        try:
            self.client.put_object(**kwargs)
        except Exception as exc:
            if self._code(exc) in ("PreconditionFailed", "412", "ConditionalRequestConflict", "409"):
                raise Conflict("The list was changed by someone else just now.") from exc
            raise

    def update(self, change, attempts: int = 5) -> tuple[list[dict], bool]:
        """Apply change(entries) -> (entries, changed) with optimistic locking."""

        for _ in range(attempts):
            entries, etag = self.load()
            new_entries, changed = change(entries)
            if not changed:
                return new_entries, False
            try:
                self.save(new_entries, etag)
                return new_entries, True
            except Conflict:
                continue
        raise AllowlistError("Could not update the list after several attempts. Try again.")

    def delete(self) -> None:
        self.client.delete_object(Bucket=self.bucket, Key=self.key)


# ---------------------------------------------------------------------------
# Environment-facing pieces
# ---------------------------------------------------------------------------

def make_store() -> Store:
    try:
        import boto3
    except ImportError as exc:
        raise AllowlistError("boto3 is required: pip install boto3") from exc
    return Store(boto3.client("s3", region_name=REGION))


def current_ip() -> str:
    """The public IP this machine reaches AWS from, as an IPv4 /32."""

    try:
        with urllib.request.urlopen(IP_LOOKUP_URL, timeout=10) as response:  # nosec B310 - fixed https URL
            text = response.read().decode().strip()
    except Exception as exc:
        raise AllowlistError(
            f"Could not detect your public IP from {IP_LOOKUP_URL} ({exc}). "
            "Add it by hand: python3 scripts/python/allowlist.py add <ip>/32"
        ) from exc
    return normalize_cidr(text if "/" in text else f"{text}/32")


def write_tfvars(cidrs: list[str], path: Path | None = None) -> Path:
    path = path or TFVARS_FILE   # resolved at call time so tests can redirect it
    path.write_text(json.dumps({"allowed_cidrs": cidrs}, indent=2) + "\n", encoding="utf-8")
    return path


def sync(store: Store | None = None, ip_lookup=None, path: Path | None = None) -> list[str]:
    """Add the current IP if it is new, then write the Terraform variable file."""

    store = store or make_store()
    me = (ip_lookup or current_ip)()
    entries, changed = store.update(lambda current: add_entry(current, me, "auto", seen=True))
    cidrs = cidrs_of(entries)
    write_tfvars(cidrs, path)
    if changed and me in cidrs:
        print(f"  Allowlist: {me} is allowed (list now has {len(cidrs)} entr{'y' if len(cidrs) == 1 else 'ies'}).")
    if len(cidrs) > WARN_ABOVE:
        print(f"  WARNING: {len(cidrs)} entries. EKS allows at most 40 including Jenkins. "
              "Run: python3 scripts/python/allowlist.py prune --older-than 30")
    return cidrs


def delete_all(store: Store | None = None, path: Path | None = None) -> None:
    path = path or TFVARS_FILE
    store = store or make_store()
    store.delete()
    if path.exists():
        path.unlink()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _print_list(entries: list[dict], me: str | None) -> None:
    if not entries:
        print("The allowlist is empty.")
        return
    width = max(len(e["cidr"]) for e in entries)
    print(f"{'CIDR'.ljust(width)}  ADDED       LAST SEEN   LABEL")
    for e in sorted(entries, key=lambda x: x["cidr"]):
        mark = "  <- you" if me and e["cidr"] == me else ""
        print(f"{e['cidr'].ljust(width)}  {e.get('added', '?'):10}  {e.get('last_seen', '?'):10}  "
              f"{e.get('label', '')}{mark}")


def _confirm(question: str, assume_yes: bool) -> bool:
    if assume_yes:
        return True
    return input(f"{question} (yes/no): ").strip().lower() in ("y", "yes")


def _best_effort_ip() -> str | None:
    try:
        return current_ip()
    except AllowlistError:
        return None


def _after_change(store: Store) -> None:
    entries, _ = store.load()
    write_tfvars(cidrs_of(entries))
    print("Saved. To apply it to AWS run: python3 scripts/python/bootstrap.py infra "
          "(then re-run the platform-infra job to update the EKS API list).")


def main(argv: list[str] | None = None, store: Store | None = None) -> int:
    parser = argparse.ArgumentParser(description="Additive operator IP allowlist")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("list", help="show the allowed CIDRs")
    sub.add_parser("sync", help="add my current IP if it is new and write the Terraform variable file")
    p_add = sub.add_parser("add", help="allow another IP or CIDR")
    p_add.add_argument("cidr")
    p_add.add_argument("--label", default="")
    p_rm = sub.add_parser("remove", help="stop allowing one CIDR")
    p_rm.add_argument("cidr")
    p_rm.add_argument("--force", action="store_true", help="allow removing the IP you are connecting from")
    p_pr = sub.add_parser("prune", help="remove entries that have not been used for a while")
    p_pr.add_argument("--older-than", type=int, default=30, metavar="DAYS")
    p_pr.add_argument("--all-but-current", action="store_true",
                      help="remove every entry except the IP you are connecting from")
    p_pr.add_argument("--yes", action="store_true")
    sub.add_parser("delete", help="delete the whole list (used when destroying the deployment)")

    args = parser.parse_args(argv)

    try:
        store = store or make_store()

        if args.command == "list":
            entries, _ = store.load()
            _print_list(entries, _best_effort_ip())
            return 0

        if args.command == "sync":
            sync(store)
            return 0

        if args.command == "add":
            cidr = normalize_cidr(args.cidr)
            _, changed = store.update(lambda cur: add_entry(cur, cidr, args.label))
            print(f"{cidr} added." if changed else f"{cidr} was already allowed. No change.")
            _after_change(store)
            return 0

        if args.command == "remove":
            cidr = normalize_cidr(args.cidr)
            if not args.force and cidr == _best_effort_ip():
                print("That is the IP you are connecting from; removing it would lock you out. "
                      "Use --force if you are sure.")
                return 1
            _, changed = store.update(lambda cur: remove_entry(cur, cidr))
            print(f"{cidr} removed." if changed else f"{cidr} was not in the list. No change.")
            _after_change(store)
            return 0

        if args.command == "prune":
            me = _best_effort_ip()
            if me is None:
                print("Cannot detect your current IP, so I cannot protect it from pruning. Aborting.")
                return 1
            days = None if args.all_but_current else args.older_than
            entries, _ = store.load()
            doomed = stale_entries(entries, older_than_days=days, keep={me})
            if not doomed:
                print("Nothing to prune.")
                return 0
            print("Would remove:")
            _print_list(doomed, me)
            if not _confirm("Remove these entries?", args.yes):
                print("Nothing changed.")
                return 0
            gone = {e["cidr"] for e in doomed}
            store.update(lambda cur: ([e for e in cur if e["cidr"] not in gone], True))
            _after_change(store)
            return 0

        if args.command == "delete":
            delete_all(store)
            print("Allowlist deleted.")
            return 0

    except AllowlistError as exc:
        print(f"ERROR: {exc}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
