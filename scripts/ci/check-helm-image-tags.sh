#!/usr/bin/env bash
# Contract test: the value keys the CI pipeline sets for image repository/tag
# (Jenkinsfile.app-cicd, 'Prepare Helm Arguments') must actually change the
# image reference the chart renders — for EVERY service.
#
# Why this exists: the pipeline once set `services.<svc>.image.tag` while the
# chart reads `backendServices.<svc>.image.tag`. Helm silently accepts unknown
# keys, so every backend quietly deployed at global.imageTag — a tag that only
# exists for the services rebuilt in that run.
#
# Usage: scripts/ci/check-helm-image-tags.sh [values-file]
set -euo pipefail

CHART="helm/catalogix-hc"
VALUES="${1:-$CHART/values-dev.yaml}"
REGISTRY="registry.example.test"
GLOBAL_TAG="GLOBAL-SENTINEL"

mapfile -t BACKENDS < <(python3 - "$VALUES" <<'PY'
import sys, yaml
with open(sys.argv[1]) as f:
    print("\n".join(yaml.safe_load(f)["backendServices"].keys()))
PY
)
[ "${#BACKENDS[@]}" -gt 0 ] || { echo "FAIL: no backendServices found in $VALUES"; exit 1; }

SET_ARGS=(--set "global.imageRegistry=${REGISTRY}" --set "global.imageTag=${GLOBAL_TAG}" --set "global.cloudProvider=aws")
for svc in "${BACKENDS[@]}"; do
    SET_ARGS+=(--set "backendServices.${svc}.image.repository=catalogix-${svc}" --set "backendServices.${svc}.image.tag=tag-${svc}")
done
for svc in frontend gateway; do
    SET_ARGS+=(--set "${svc}.image.repository=catalogix-${svc}" --set "${svc}.image.tag=tag-${svc}")
done

RENDERED="$(helm template catalogix "$CHART" --namespace catalogix -f "$VALUES" "${SET_ARGS[@]}")"

fail=0
for svc in "${BACKENDS[@]}" frontend gateway; do
    expected="image: \"${REGISTRY}/catalogix-${svc}:tag-${svc}\""
    if ! grep -qF "$expected" <<<"$RENDERED"; then
        echo "FAIL: ${svc}: expected ${expected} in the rendered chart"
        fail=1
    fi
done

# Every application image was given its own tag, so the global fallback tag
# must not appear on any image line at all.
if grep -E "^\s*image:.*:${GLOBAL_TAG}\"" <<<"$RENDERED"; then
    echo "FAIL: at least one image ignored its per-service tag and fell back to global.imageTag (lines above)"
    fail=1
fi

[ "$fail" -eq 0 ] && echo "OK: all $((${#BACKENDS[@]} + 2)) images honour their per-service repository/tag values."
exit "$fail"
