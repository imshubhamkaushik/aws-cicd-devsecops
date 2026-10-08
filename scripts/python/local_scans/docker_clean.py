#!/usr/bin/env python3
"""Safely clean Catalogix Docker resources."""

from __future__ import annotations

import argparse

from common import COMPOSE_FILE, REPO_ROOT, main_guard, require_docker, run


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Clean Catalogix Docker resources without a blanket docker system prune."
    )
    parser.add_argument(
        "--images",
        action="store_true",
        help="Remove Compose local application images after bringing the stack down.",
    )
    parser.add_argument(
        "--volumes",
        action="store_true",
        help="Remove Compose volumes. This deletes local PostgreSQL/RabbitMQ/etc. data.",
    )
    parser.add_argument(
        "--builder-cache",
        action="store_true",
        help="Prune Docker build cache (not containers/images).",
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Confirm destructive volume removal.",
    )
    return parser.parse_args()


def main() -> int:
    require_docker()
    args = parse_args()

    if args.volumes and not args.yes:
        raise RuntimeError(
            "--volumes deletes local Compose database/data volumes. "
            "Re-run with --volumes --yes if that is intentional."
        )

    down = [
        "docker",
        "compose",
        "-f",
        str(COMPOSE_FILE),
        "down",
        "--remove-orphans",
    ]

    if args.images:
        # `--rmi local` skips images that have an explicit `image:` name, which every
        # Catalogix service has, so it would remove nothing. `all` removes the images
        # this compose file uses (including pulled ones such as postgres) - they are
        # simply re-pulled on the next `up`.
        down += ["--rmi", "all"]

    if args.volumes:
        down.append("--volumes")

    run(down, cwd=REPO_ROOT)

    if args.builder_cache:
        run(["docker", "builder", "prune", "-f"], cwd=REPO_ROOT)

    print("\nCatalogix Docker cleanup completed.")
    return 0


if __name__ == "__main__":
    main_guard(main)
