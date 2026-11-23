#!/usr/bin/env bash
# Publishes a base image (a Dockerfile under base/) for amd64 and arm64 to ghcr.io as <repository>-<name>:base;
# consumers pin the digest, `base` only moves. The optional third argument names the base image it builds on (`BASE`).
# With GITHUB_OUTPUT set it writes `name` (the image without its tag) and `digest` (the index's) there.
# Usage: scripts/publish-base-image.sh <name> <dockerfile> [from-name]
#   Needs: docker, GITHUB_REPOSITORY, GITHUB_ACTOR, GITHUB_TOKEN.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
name="$1"
dockerfile="$2"
from="${3:-}"
image="ghcr.io/${GITHUB_REPOSITORY,,}-${name}:base"

build_args=()
[[ -n "$from" ]] && build_args+=(--build-arg "BASE=ghcr.io/${GITHUB_REPOSITORY,,}-${from}:base")

metadata="$(mktemp)"
trap 'rm -f "$metadata"' EXIT

printf '%s' "$GITHUB_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
docker run --privileged --rm tonistiigi/binfmt --install arm64 >/dev/null
docker buildx create --use >/dev/null
docker buildx build --platform linux/amd64,linux/arm64 --sbom=true --provenance=mode=max --tag "$image" --push \
  --metadata-file "$metadata" --build-context "scripts=$REPO_ROOT/orchestrator-backend/scripts" --file "$REPO_ROOT/base/$dockerfile" "${build_args[@]}" "$REPO_ROOT/base"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "name=${image%:*}" >> "$GITHUB_OUTPUT"
  echo "digest=$(jq -r '."containerimage.digest"' "$metadata")" >> "$GITHUB_OUTPUT"
fi
