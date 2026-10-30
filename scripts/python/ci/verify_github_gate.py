#!/usr/bin/env python3
"""Prove that a commit passed the GitHub Actions CI gate before Jenkins deploys it.

The repository's quality and security checks run in GitHub Actions (.github/workflows/ci.yaml).
That workflow ends with one aggregate job, named `ci-gate`, which succeeds only if every other job
succeeded. `Jenkinsfile.app-cicd` calls this script with the commit it is about to deploy:

    GH_TOKEN=... python3 scripts/python/ci/verify_github_gate.py \
        --repo-url https://github.com/<owner>/<repo>.git --sha <40-hex> --check-name ci-gate

The script asks the GitHub API for that commit's `ci-gate` check run and waits while it is queued or
running, because Jenkins can pick a commit up before the workflow has finished. It exits 0 only when
the check run completed with the conclusion `success`. Any other outcome, or a timeout, exits 1.

Jenkins is not reachable from the internet, so nothing is pushed to it: Jenkins polls Git and pulls
the result from GitHub. The token (GH_TOKEN) needs read access to the repository's checks; a classic
token with the `repo` scope is enough. It is read from the environment, never from the command line.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Callable

API = "https://api.github.com"
SHA_RE = re.compile(r"^[0-9a-fA-F]{40,64}$")
REPO_RE = re.compile(r"github\.com[:/](?P<owner>[^/]+)/(?P<repo>[^/]+?)(?:\.git)?/?$")

SUCCESS, PENDING, FAILURE = "success", "pending", "failure"


class GateError(Exception):
    """A problem that retrying cannot fix (bad token, unknown repository, bad input)."""


class TransientError(Exception):
    """A problem that is worth retrying until the deadline (network error, GitHub 5xx)."""


def parse_repo(url: str) -> tuple[str, str]:
    """Return (owner, repo) from a GitHub HTTPS or SSH clone URL."""
    match = REPO_RE.search(url.strip())
    if not match:
        raise GateError(f"Not a GitHub repository URL: {url!r}")
    return match.group("owner"), match.group("repo")


def classify(payload: dict, check_name: str) -> tuple[str, str]:
    """Turn a check-runs API payload into (state, human-readable message)."""
    runs = [run for run in payload.get("check_runs", []) if run.get("name") == check_name]
    if not runs:
        return PENDING, f"no '{check_name}' check run exists for this commit yet"
    latest = max(runs, key=lambda run: run.get("id", 0))  # a re-run creates a newer check run
    if latest.get("status") != "completed":
        return PENDING, f"'{check_name}' is {latest.get('status', 'unknown')}"
    conclusion = latest.get("conclusion")
    url = latest.get("html_url", "")
    if conclusion == "success":
        return SUCCESS, f"'{check_name}' succeeded {url}".strip()
    return FAILURE, f"'{check_name}' finished with conclusion '{conclusion}' {url}".strip()


def fetch_check_runs(owner: str, repo: str, sha: str, check_name: str, token: str, timeout: int = 20) -> dict:
    """Call the GitHub API once. Raises GateError or TransientError."""
    query = urllib.parse.urlencode({"check_name": check_name, "filter": "latest", "per_page": 100})
    url = f"{API}/repos/{owner}/{repo}/commits/{sha}/check-runs?{query}"
    req = urllib.request.Request(  # noqa: S310 - fixed https://api.github.com base
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "catalogix-verify-gate",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code in (401, 403, 404, 422):
            raise GateError(
                f"GitHub API returned HTTP {exc.code} for {owner}/{repo}@{sha[:12]}. "
                "Check that GH_TOKEN is valid, can read the repository's checks, and that the commit exists on GitHub."
            ) from exc
        raise TransientError(f"GitHub API returned HTTP {exc.code}") from exc
    except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
        raise TransientError(f"GitHub API request failed: {exc}") from exc


def wait_for_gate(
    fetch: Callable[[], dict],
    check_name: str,
    *,
    timeout: float,
    interval: float,
    sleep: Callable[[float], None] = time.sleep,
    clock: Callable[[], float] = time.monotonic,
    log: Callable[[str], None] = print,
) -> tuple[str, str]:
    """Poll until the check run finishes or the deadline passes. Returns (state, message)."""
    deadline = clock() + timeout
    last = "no answer from GitHub yet"
    while True:
        try:
            state, message = classify(fetch(), check_name)
        except TransientError as exc:
            state, message = PENDING, f"{exc} (will retry)"
        if state in (SUCCESS, FAILURE):
            return state, message
        last = message
        if clock() + interval > deadline:
            return FAILURE, f"timed out after {int(timeout)}s waiting for the gate; last status: {last}"
        log(f"Waiting for the CI gate: {message}")
        sleep(interval)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo-url", required=True, help="GitHub clone URL of the repository")
    parser.add_argument("--sha", required=True, help="full commit SHA to verify")
    parser.add_argument("--check-name", default="ci-gate", help="name of the aggregate check run (default: ci-gate)")
    parser.add_argument("--timeout", type=int, default=1500, help="seconds to wait for the check run (default: 1500)")
    parser.add_argument("--interval", type=int, default=30, help="seconds between polls (default: 30)")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    token = os.environ.get("GH_TOKEN", "").strip()
    if not token:
        print("ERROR: GH_TOKEN is not set.", file=sys.stderr)
        return 1
    if not SHA_RE.match(args.sha):
        print("ERROR: --sha must be a full commit SHA.", file=sys.stderr)
        return 1
    try:
        owner, repo = parse_repo(args.repo_url)
        state, message = wait_for_gate(
            lambda: fetch_check_runs(owner, repo, args.sha, args.check_name, token),
            args.check_name,
            timeout=args.timeout,
            interval=args.interval,
        )
    except GateError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    if state == SUCCESS:
        print(f"CI gate passed for {owner}/{repo}@{args.sha[:12]}: {message}")
        return 0
    print(f"ERROR: CI gate did not pass for {owner}/{repo}@{args.sha[:12]}: {message}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
