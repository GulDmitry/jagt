#!/usr/bin/env bash
# Publishes the OS base image (base/Dockerfile) for amd64 and arm64 to ghcr.io; consumers pin the digest, `base` only moves.
# With GITHUB_OUTPUT set it writes `name` (the image without its tag) and `digest` (the index's) there.
# Usage: scripts/publish-base-image.sh      Needs: docker, GITHUB_REPOSITORY, GITHUB_ACTOR, GITHUB_TOKEN.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
image="ghcr.io/${GITHUB_REPOSITORY,,}-base:base"

metadata="$(mktemp)"
trap 'rm -f "$metadata"' EXIT

printf '%s' "$GITHUB_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
docker run --privileged --rm tonistiigi/binfmt --install arm64 >/dev/null
docker buildx create --use >/dev/null
docker buildx build --platform linux/amd64,linux/arm64 --sbom=true --provenance=mode=max --tag "$image" --push \
  --metadata-file "$metadata" "$REPO_ROOT/base"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "name=${image%:*}" >> "$GITHUB_OUTPUT"
  echo "digest=$(jq -r '."containerimage.digest"' "$metadata")" >> "$GITHUB_OUTPUT"
fi
