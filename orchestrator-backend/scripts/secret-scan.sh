#!/bin/sh
# Scans the tree and the full history for a committed secret; run from the repository root, which needs full history.
# A finding names the file and the rule; a false positive gets an entry with its reason in .gitleaks.toml.
set -eu
version=8.30.1
sha256=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb
dir="$(mktemp -d)"
curl -fsSL -o "$dir/gitleaks.tar.gz" "https://github.com/gitleaks/gitleaks/releases/download/v$version/gitleaks_${version}_linux_x64.tar.gz"
echo "$sha256  $dir/gitleaks.tar.gz" | sha256sum -c -
tar -xzf "$dir/gitleaks.tar.gz" -C "$dir" gitleaks
"$dir/gitleaks" dir --redact --no-banner --verbose .
"$dir/gitleaks" git --redact --no-banner --verbose .
