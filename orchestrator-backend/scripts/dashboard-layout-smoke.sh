#!/usr/bin/env bash
# Real-PTY regression test for the Master dashboard layout (Lanterna full-screen TUI).
#
# WHY a shell/tmux test and not a JUnit test: the layout is produced by a real terminal (full-screen
# back-buffer, cursor addressing, resize). A virtual/dumb terminal cannot exercise any of that, so it
# cannot catch these bugs. tmux gives a real PTY; `capture-pane -p` reads the rendered screen. This is the
# ONLY kind of test that catches dashboard-layout regressions.
#
# INVARIANTS asserted after startup, resize-taller, resize-shorter, and task-count changes:
#   1. exactly ONE dashboard header on screen (a resize must never orphan a ghost copy);
#   2. the `jagt>` input line is present and pinned to the BOTTOM row of the pane;
#   3. the dashboard sits ABOVE the input line (header row < input row) — one integrated screen, no gap.
#
# Bugs this pins (must never recur): the dashboard/prompt separated by a huge blank gap on full-screen,
# and the prompt "flying" away from the dashboard after the terminal is resized.
#
# Usage: dashboard-layout-smoke.sh [/path/to/jagt.jar]
# Exits 0 if every invariant holds in every scenario, 1 otherwise. Leaves no trace (throwaway tmux + root).
set -u

JAR="${1:-$(cd "$(dirname "$0")/.." && pwd)/build/libs/jagt.jar}"
if [[ ! -f "$JAR" ]]; then echo "jar not found: $JAR (run ./gradlew bootJar)"; exit 2; fi
command -v tmux >/dev/null || { echo "tmux required"; exit 2; }
# The jar is launched INSIDE a tmux pane, whose shell rebuilds PATH from the system profile — so a JDK that
# only exists in the caller's environment (a CI runner's setup-java, an sdkman shim, a Nix profile) is simply
# not found there and the pane prints "java: command not found" while this script waits for a dashboard that
# will never appear. Resolve java HERE and send an absolute path.
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null; then
    JAVA="$(command -v java)"
else
    echo "java not found (set JAVA_HOME or put java on PATH)"; exit 2
fi

SESSION="jagt-layout-smoke-$$"
PORT=8298
ROOT="$(mktemp -d)"
FAILS=0

cleanup() { tmux kill-session -t "$SESSION" 2>/dev/null; rm -rf "$ROOT"; }
trap cleanup EXIT

# The fixture is a handful of flat objects, so printf writes it. jagt's own tools are java, git, tmux and node;
# a test harness has no business adding a language runtime to that list just to emit ten lines of JSON.
gen_state() { # $1 = number of tasks
  local count="$1" now entries="" separator="" i
  now=$(( $(date +%s) * 1000 ))
  for (( i = 1; i <= count; i++ )); do
    entries="$entries$separator$(printf '
    "ABC-%d": {
      "project": "demo",
      "worktreePath": "/tmp/wt/%d",
      "status": "IN_PROGRESS",
      "lastActiveTimestamp": %d,
      "message": "x",
      "alias": "p%d",
      "remoteUrl": "git@example.com:demo/demo.git",
      "title": "Fictional task %d",
      "mrUrl": null
    }' "$(( 1000 + i ))" "$i" "$(( now - i * 60000 ))" "$i" "$i")"
    separator=","
  done
  printf '{ "tasks": {%s\n} }\n' "$entries" > "$ROOT/state.json"
}

assert_layout() { # $1 = scenario label
  local label="$1" cap headers irow drow paneh
  cap="$(tmux capture-pane -p -t "$SESSION")"
  paneh="$(tmux display -p -t "$SESSION" '#{pane_height}')"
  headers="$(printf '%s\n' "$cap" | grep -c 'jagt orchestrator —')"
  irow="$(printf '%s\n' "$cap" | grep -n 'jagt>' | tail -1 | cut -d: -f1)"
  drow="$(printf '%s\n' "$cap" | grep -n 'jagt orchestrator —' | tail -1 | cut -d: -f1)"
  if [[ "$headers" != "1" ]]; then
    echo "FAIL [$label]: expected 1 dashboard header on screen, found $headers (ghost/orphan after resize)"
    FAILS=$((FAILS+1)); return
  fi
  if [[ -z "$irow" ]]; then
    echo "FAIL [$label]: input line 'jagt>' not visible on screen"
    FAILS=$((FAILS+1)); return
  fi
  if (( irow != paneh )); then
    echo "FAIL [$label]: input line at row $irow, expected the bottom row $paneh (not pinned to bottom)"
    FAILS=$((FAILS+1)); return
  fi
  if (( drow >= irow )); then
    echo "FAIL [$label]: dashboard@$drow not above input@$irow"
    FAILS=$((FAILS+1)); return
  fi
  echo "ok   [$label]: dashboard@$drow input@$irow (bottom, paneh=$paneh) headers=$headers"
}

printf 'orchestrator:\n  dashboard: {refreshSeconds: 2, reservedRows: 8}\n  viewer: {tmuxSession: "%s", viewMode: shared}\n  projects: {}\n' "$SESSION" > "$ROOT/jagt.yml"
: > "$ROOT/mcp_client.js"
gen_state 2

tmux kill-session -t "$SESSION" 2>/dev/null
tmux new-session -d -s "$SESSION" -x 120 -y 30
tmux send-keys -t "$SESSION" "ORCHESTRATOR_ROOT=$ROOT $JAVA -jar $JAR --server.port=$PORT --orchestrator.ui=tui --orchestrator.open-warp-window=false --orchestrator.startup-checks=false" Enter
for _ in $(seq 1 40); do curl -s "localhost:$PORT/state" >/dev/null 2>&1 && break; sleep 1; done
sleep 3

assert_layout "start h30 / 2 tasks"
tmux resize-window -t "$SESSION" -x 120 -y 55; sleep 3; assert_layout "resize taller h55"
tmux resize-window -t "$SESSION" -x 120 -y 20; sleep 3; assert_layout "resize shorter h20"
tmux resize-window -t "$SESSION" -x 120 -y 45; sleep 3; assert_layout "resize taller again h45"
gen_state 6; sleep 3; assert_layout "grow to 6 tasks h45"
gen_state 1; sleep 3; assert_layout "shrink to 1 task h45"

if (( FAILS == 0 )); then echo "PASS: all layout invariants held"; exit 0; fi
echo "FAILED: $FAILS layout violation(s)"; exit 1
