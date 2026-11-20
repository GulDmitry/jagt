#!/usr/bin/env bash
# Proves the installers end to end against a fake release feed: install 0.0.1, start the launcher, let the
# release check keep 0.0.2, run the board's `upgrade` verb, see the launcher restart jagt on 0.0.2.
# Usage: scripts/install-check.sh <bootJar>      (Windows: Git Bash, installs through install.ps1)
set -euo pipefail

jar=${1:?usage: install-check.sh <bootJar>}
[ -f "$jar" ] || { echo "install-check: no jar at $jar" >&2; exit 2; }
repo=$(cd "$(dirname "$0")/../.." && pwd)
stage=setup
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) windows=true ;; *) windows=false ;; esac
py=$(command -v python3 || command -v python)

native() { if [ "$windows" = true ]; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

work=$(mktemp -d)
launcher_pid=
server_pid=
cleanup() {
  code=$?
  set +e
  if [ "$code" -ne 0 ]; then
    echo "install-check: FAILED at stage '$stage'" >&2
    [ -f "$work.log" ] && tail -n 60 "$work.log" >&2
  fi
  [ -n "$launcher_pid" ] && kill "$launcher_pid" 2>/dev/null
  if [ "$windows" = true ]; then
    taskkill //F //T //PID "$(ps -p "$launcher_pid" -W 2>/dev/null | awk 'NR==2{print $4}')" >/dev/null 2>&1
    taskkill //F //IM java.exe >/dev/null 2>&1
  else
    pkill -f "$work/install/runtime" 2>/dev/null
  fi
  [ -n "$server_pid" ] && kill "$server_pid" 2>/dev/null
  sleep 2
  rm -rf "$work" "$work.log"
  exit "$code"
}
trap cleanup EXIT

wait_for() { # <seconds> <description> <command...>
  limit=$1
  what=$2
  shift 2
  end=$((SECONDS + limit))
  until "$@" >/dev/null 2>&1; do
    [ "$SECONDS" -lt "$end" ] || { echo "install-check: timed out after ${limit}s: $what" >&2; return 1; }
    sleep 1
  done
}

free_port() { "$py" -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1])'; }

sum_of() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1; else shasum -a 256 "$1" | cut -d ' ' -f 1; fi; }

stage="lay out the fake feed"
feed="$work/feed"
for v in 0.0.1 0.0.2; do
  mkdir -p "$feed/download/v$v"
  cp "$jar" "$feed/download/v$v/jagt-$v.jar"
  echo "$(sum_of "$jar")  jagt-$v.jar" >"$feed/download/v$v/jagt-$v.sha256"
done
echo 0.0.1 >"$feed/latest-version"
cat >"$work/feed.py" <<'PY'
import http.server, sys
root = sys.argv[1]
class Feed(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=root, **k)
    def do_GET(self):
        if self.path == "/latest":
            self.send_response(302)
            self.send_header("Location", "/tag/v" + open(root + "/latest-version").read().strip())
            self.send_header("Content-Length", "0")
            self.end_headers()
        else:
            super().do_GET()
    def log_message(self, *a):
        pass
http.server.ThreadingHTTPServer(("127.0.0.1", int(sys.argv[2])), Feed).serve_forever()
PY
feed_port=$(free_port)
"$py" "$(native "$work/feed.py")" "$(native "$feed")" "$feed_port" &
server_pid=$!
feed_url="http://127.0.0.1:$feed_port"
wait_for 30 "the fake feed answers" curl --fail --silent --output /dev/null "$feed_url/download/v0.0.1/jagt-0.0.1.sha256"

stage="install 0.0.1"
home="$work/install"
bin="$work/bin"
export JAGT_VERSION=0.0.1 JAGT_INSTALL_FEED="$feed_url"
export JAGT_HOME="$(native "$home")" JAGT_BIN="$(native "$bin")"
if [ "$windows" = true ]; then
  timeout 600 pwsh -NoProfile -File "$(native "$repo/install.ps1")"
  launcher="$bin/jagt.cmd"
else
  timeout 600 sh "$repo/install.sh"
  launcher="$bin/jagt"
fi
unset JAGT_VERSION
[ -f "$home/versions/0.0.1/jagt.jar" ] || { echo "install-check: no versions/0.0.1/jagt.jar" >&2; exit 1; }
[ -f "$launcher" ] || { echo "install-check: no launcher" >&2; exit 1; }

stage="start jagt under the launcher"
port=$(free_port)
unset ORCHESTRATOR_ROOT ORCHESTRATOR_CONFIG_FILE
export JAGT__orchestrator__releaseFeed__url="$feed_url"
args=(--server.port="$port" --orchestrator.terminal=none --orchestrator.startup-checks=false)
if [ "$windows" = true ]; then
  cmd.exe //c "$(cygpath -w "$launcher")" "${args[@]}" >"$work.log" 2>&1 &
else
  "$launcher" "${args[@]}" >"$work.log" 2>&1 &
fi
launcher_pid=$!
api="http://127.0.0.1:$port"
wait_for 180 "/state answers 200 on 0.0.1" curl --fail --silent --output /dev/null "$api/state"
for placed in jagt.desktop.yml.dist roles/session/brief.md.dist; do
  [ -f "$home/$placed" ] || { echo "install-check: the jar placed no $placed in the install home" >&2; exit 1; }
done
[ ! -e "$home/jagt.yml" ] || { echo "install-check: a jagt.yml appeared in the install home" >&2; exit 1; }

stage="keep 0.0.2 beside 0.0.1"
echo 0.0.2 >"$feed/latest-version"
curl --fail --silent --show-error --request POST --header "Origin: $api" "$api/api/commands/run?about=releasecheck" >/dev/null
wait_for 120 "versions/0.0.2/jagt.jar is kept" test -f "$home/versions/0.0.2/jagt.jar"
upgrade_listed() { curl --fail --silent "$api/api/commands" | grep -q '"upgrade"'; }
wait_for 60 "the board offers upgrade" upgrade_listed

stage="upgrade through the board's command"
curl --fail --silent --show-error --request POST --header "Origin: $api" "$api/api/commands/upgrade"
echo
upgrade_gone() { ! upgrade_listed; }
wait_for 180 "jagt restarts and no longer offers upgrade" upgrade_gone
wait_for 60 "/state answers 200 on 0.0.2" curl --fail --silent --output /dev/null "$api/state"

stage="current names 0.0.2"
if [ "$windows" = true ]; then
  [ "$(tr -d '\r\n ' <"$home/current")" = 0.0.2 ] || { echo "install-check: current is not 0.0.2" >&2; exit 1; }
else
  [ "$(readlink "$home/current")" = versions/0.0.2 ] || { echo "install-check: current is not versions/0.0.2" >&2; exit 1; }
fi
[ -f "$home/versions/0.0.1/jagt.jar" ] || { echo "install-check: 0.0.1 was not kept" >&2; exit 1; }
stage=done
echo "install-check: ok"
