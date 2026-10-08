#!/usr/bin/env python3
"""Run Gitleaks locally in Docker (same image and config as Jenkinsfile.app-cicd).

Mode is chosen automatically:
  * a .git folder exists  -> scan the full commit history (what CI does)
  * no .git (e.g. a zip)  -> scan the working tree with --no-git
In working-tree mode the git-ignored local .env is skipped, because it is never committed.
Everything else, including .env.example, is still scanned.
"""

from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

from common import REPO_MOUNT, REPO_ROOT, has_git_history, main_guard, require_docker, run

DEFAULT_IMAGE = "zricethezav/gitleaks:v8.21.2"  # keep in sync with Jenkinsfile.app-cicd


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the Gitleaks secret scan in Docker.")
    parser.add_argument("--image", default=DEFAULT_IMAGE, help=f"Gitleaks image (default: {DEFAULT_IMAGE}).")
    parser.add_argument("--no-redact", action="store_true", help="Show the matched secret in the output.")
    parser.add_argument("--working-tree-only", action="store_true", help="Force --no-git even if .git exists.")
    return parser.parse_args()


def main() -> int:
    require_docker()
    args = parse_args()

    no_git = args.working_tree_only or not has_git_history()
    if no_git and not args.working_tree_only:
        print("No .git folder found: scanning the working tree only (history cannot be scanned).")

    mounts = ["-v", REPO_MOUNT]
    config_path = "/repo/.gitleaks.toml"

    with tempfile.TemporaryDirectory(prefix="catalogix-gitleaks-") as tmp:
        if no_git:
            # Extend the repo config and skip only the untracked local .env.
            cfg = Path(tmp) / "gitleaks.toml"
            cfg.write_text(
                'title = "Catalogix local working-tree scan"\n'
                "[extend]\n"
                'path = "/repo/.gitleaks.toml"\n'
                "[allowlist]\n"
                'description = "git-ignored local env file"\n'
                "paths = ['''^\\.env$''']\n",
                encoding="utf-8",
            )
            mounts += ["-v", f"{Path(tmp).as_posix()}:/cfg:ro"]
            config_path = "/cfg/gitleaks.toml"

        command = [
            "docker", "run", "--rm", *mounts, args.image,
            "detect", "--source=/repo", f"--config={config_path}", "--no-banner", "--exit-code=1",
        ]
        if not args.no_redact:
            command.append("--redact")
        if no_git:
            command.append("--no-git")

        run(command, cwd=REPO_ROOT)

    print("\nGitleaks scan PASSED.")
    return 0


if __name__ == "__main__":
    main_guard(main)
