#!/bin/sh
# Installs jagt for the current user, no sudo: a release jar, a Temurin 25 JRE, a launcher on PATH.
# JAGT_VERSION pins a release (default: the latest), JAGT_HOME the install root, JAGT_BIN the launcher's
# folder, JAGT_INSTALL_FEED and JAGT_INSTALL_JRE_FEED the two download bases. With gh on PATH the release's
# provenance attestation of the jar and its checksums is verified against JAGT_INSTALL_REPO.
set -eu

REPO=${JAGT_INSTALL_REPO:-GulDmitry/jagt}
FEED=${JAGT_INSTALL_FEED:-https://github.com/$REPO/releases}
JRE_FEED=${JAGT_INSTALL_JRE_FEED:-https://api.adoptium.net/v3/binary/latest/25/ga}
BIN=${JAGT_BIN:-$HOME/.local/bin}

fail() {
  echo "install.sh: $*" >&2
  exit 1
}

fetch() {
  curl --fail --silent --show-error --location --connect-timeout 15 --max-time 600 "$@"
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

verify() {
  want=$(printf '%s' "$2" | cut -d ' ' -f 1)
  [ -n "$want" ] || fail "no checksum published for $1"
  [ "$(sha256_of "$1")" = "$want" ] || fail "checksum mismatch for $(basename "$1"), nothing installed"
}

attest() {
  if command -v gh >/dev/null 2>&1; then
    for asset in "$@"; do
      gh attestation verify "$asset" --repo "$REPO" >/dev/null ||
        fail "provenance verification failed for $(basename "$asset"), nothing installed"
    done
  else
    echo "install.sh: gh not found, provenance not verified, only the sha256 was checked" >&2
  fi
}

case "$(uname -s)" in
  Darwin)
    os=mac
    default_root="$HOME/Library/Application Support/jagt"
    ;;
  Linux)
    os=linux
    default_root="${XDG_DATA_HOME:-$HOME/.local/share}/jagt"
    ;;
  *) fail "unsupported system $(uname -s): macOS and Linux only" ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) arch=x64 ;;
  arm64 | aarch64) arch=aarch64 ;;
  *) fail "unsupported architecture $(uname -m)" ;;
esac
ROOT=${JAGT_HOME:-$default_root}

version=${JAGT_VERSION:-}
if [ -z "$version" ]; then
  tag=$(fetch --head --output /dev/null --write-out '%{url_effective}' "$FEED/latest") ||
    fail "cannot read the latest release from $FEED"
  version=${tag##*/}
fi
version=${version#v}
case "$version" in
  *[!0-9A-Za-z._-]* | '') fail "invalid version '$version'" ;;
esac

root_was_new=false
[ -d "$ROOT" ] || root_was_new=true
mkdir -p "$ROOT"
staging=$(mktemp -d "$ROOT/.staging.XXXXXX")
cleanup() {
  rm -rf "$staging"
  if [ "$root_was_new" = true ]; then
    rmdir "$ROOT" 2>/dev/null || true # a root holding files is kept
  fi
}
trap cleanup EXIT
trap 'exit 1' INT TERM HUP

release="$FEED/download/v$version"
jar="jagt-$version.jar"
fetch --output "$staging/$jar" "$release/$jar" || fail "cannot download $jar from $release"
fetch --output "$staging/jagt-$version.sha256" "$release/jagt-$version.sha256" ||
  fail "cannot download the checksum of $jar"
verify "$staging/$jar" "$(grep " $jar\$" "$staging/jagt-$version.sha256" || true)" # no line: verify fails it
attest "$staging/$jar" "$staging/jagt-$version.sha256"

java_of() {
  if [ -x "$1/Contents/Home/bin/java" ]; then
    echo "$1/Contents/Home/bin/java"
  else
    echo "$1/bin/java"
  fi
}

if [ ! -x "$(java_of "$ROOT/runtime")" ]; then
  archive="$staging/jre.tar.gz"
  jre_url="$JRE_FEED/$os/$arch/jre/hotspot/normal/eclipse"
  final=$(curl --silent --show-error --head --connect-timeout 15 --max-time 60 --output /dev/null \
    --write-out '%{redirect_url}' "$jre_url") || fail "cannot reach the Java runtime feed at $jre_url"
  [ -n "$final" ] || final=$jre_url
  fetch --output "$archive" "$final" || fail "cannot download the Java runtime from $final"
  fetch --output "$staging/jre.sha256" "$final.sha256.txt" || fail "cannot download the runtime's checksum"
  verify "$archive" "$(cat "$staging/jre.sha256")"
  mkdir "$staging/runtime"
  tar -xzf "$archive" -C "$staging/runtime" --strip-components=1 || fail "cannot unpack the Java runtime"
  [ -x "$(java_of "$staging/runtime")" ] || fail "the Java runtime holds no java"
  rm -rf "$ROOT/runtime"
  mv "$staging/runtime" "$ROOT/runtime"
fi

mkdir -p "$ROOT/versions" "$BIN"
rm -rf "${ROOT:?}/versions/$version"
mkdir "$ROOT/versions/$version"
mv "$staging/$jar" "$ROOT/versions/$version/jagt.jar"
ln -sfn "versions/$version" "$ROOT/current"
root_was_new=false

launcher="$BIN/jagt"
{
  echo '#!/bin/sh'
  echo "root='$ROOT'"
  echo 'if [ -x "$root/runtime/Contents/Home/bin/java" ]; then java="$root/runtime/Contents/Home/bin/java"; else java="$root/runtime/bin/java"; fi'
  echo 'export JAGT_LAUNCHER=1'
  echo 'child='
  echo "trap 'stopping=1; [ -z \"\$child\" ] || kill -TERM \"\$child\" 2>/dev/null' INT TERM"
  echo 'stopping=0'
  echo 'while :; do'
  echo '  jar="$root/$(readlink "$root/current")/jagt.jar"'
  echo '  "$java" -jar "$jar" "$@" &'
  echo '  child=$!'
  echo '  wait "$child"; code=$?'
  echo '  while kill -0 "$child" 2>/dev/null; do wait "$child"; code=$?; done'
  echo '  [ "$stopping" -eq 0 ] || exit "$code"'
  echo '  [ "$code" -eq 75 ] || exit "$code"'
  echo 'done'
} >"$launcher.new"
chmod 755 "$launcher.new"
mv -f "$launcher.new" "$launcher"

echo "jagt $version installed in $ROOT, launcher $launcher"
case ":$PATH:" in
  *":$BIN:"*) ;;
  *) echo "add $BIN to your PATH to run jagt" ;;
esac
