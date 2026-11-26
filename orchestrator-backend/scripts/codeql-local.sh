#!/usr/bin/env bash
# The local twin of .github/workflows/codeql.yml: the CodeQL CLI of the bundle the pinned action downloads, over the same
# databases (java-kotlin traced over gateCodeql, javascript-typescript without a build), config and default query suite,
# ending in the same severity-error rule (codeql-errors.sh). Runs in CI's image from ci-local.sh; the bundle is cached
# under the Gradle home and verified by checksum.
# Usage: scripts/codeql-local.sh      (from orchestrator-backend)
# exits: 0 1
set -euo pipefail

action=v4.39.3 # github/codeql-action, as codeql.yml pins it; its lib/defaults.json names this bundle
bundle="codeql-bundle-v2.27.4"
case "$(uname -m)" in
    aarch64 | arm64)
        asset="codeql-bundle-linux-arm64.tar.zst"
        sha256=1c015b411c6e1c7f7e767eb8c9408fc6a2012d24d78b177cf22f29edc5772d10 ;;
    *)
        asset="codeql-bundle-linux64.tar.zst"
        sha256=da3e952c7fe978a0b6b93e75d9cbabbdf8535667ca0810bd4b2ae37bc840ffee ;;
esac

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
home="${GRADLE_USER_HOME:-$HOME/.gradle}/codeql/$bundle-$(uname -m)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [[ ! -x $home/codeql ]]; then
    echo "codeql: fetching $bundle for $action"
    curl -fsSL -o "$work/$asset" "https://github.com/github/codeql-action/releases/download/$bundle/$asset"
    echo "$sha256  $work/$asset" | sha256sum -c -
    tar --zstd -xf "$work/$asset" -C "$work"
    rm -rf "$home"
    mkdir -p "$(dirname "$home")"
    mv "$work/codeql" "$home"
    rm -f "$work/$asset"
fi
codeql="$home/codeql"
config="$root/.github/codeql/codeql-config.yml"

analyse() {
    "$codeql" database analyze "$work/$1" --format=sarif-latest --sarif-category="/language:$1" --threads=0 \
        --output="$work/$1.sarif"
}

cd "$root/orchestrator-backend"
"$codeql" database create "$work/java-kotlin" --language=java-kotlin --build-mode=manual --source-root="$root" \
    --codescanning-config="$config" --threads=0 \
    --command="$root/orchestrator-backend/gradlew -p $root/orchestrator-backend --no-daemon --no-build-cache --rerun-tasks gateCodeql"
analyse java-kotlin
"$codeql" database create "$work/javascript-typescript" --language=javascript-typescript --build-mode=none \
    --source-root="$root" --codescanning-config="$config" --threads=0
analyse javascript-typescript
"$here/codeql-errors.sh" "$work"/*.sarif
