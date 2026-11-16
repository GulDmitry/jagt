#!/usr/bin/env bash
# Publishes jagt's image for amd64 and arm64 to ghcr.io under a release version.
# The SBOM and the build provenance travel in the image index as OCI attestations.
# Usage: scripts/publish-image.sh <version>      Needs: docker, GITHUB_REPOSITORY, GITHUB_ACTOR, GITHUB_TOKEN.
set -euo pipefail

version=${1:?usage: publish-image.sh <version>}
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
image="ghcr.io/${GITHUB_REPOSITORY,,}:${version#v}"

printf '%s' "$GITHUB_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin
docker run --privileged --rm tonistiigi/binfmt --install arm64 >/dev/null
docker buildx create --use >/dev/null
docker buildx build --platform linux/amd64,linux/arm64 --sbom=true --provenance=mode=max --tag "$image" --push "$REPO_ROOT"
