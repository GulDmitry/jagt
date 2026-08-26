#!/usr/bin/env bash
# Real-PTY regression test for ONE behaviour: the console repaints when STATE CHANGES, not only when its
# timer fires.
#
# WHY it cannot be a JUnit test: the thing under test is a Lanterna screen redrawn from a background thread's
# event, observed through a real PTY. And why the timing is not flaky: the periodic refresh is configured to
# 60s here, so a dashboard that shows the new status within seconds can ONLY have been repainted by the
# StateService change event. Delete the listener and this fails; make the interval the trigger again and it
# fails.
#
# The mutation goes through `POST /mcp` on purpose — writing state.json directly (as the layout smoke test
# does) fires no listener, because nothing changed through StateService.
#
# Usage: tui-push-repaint-smoke.sh [/path/to/jagt.jar]
# Exits 0 if the repaint happened within the window, 1 otherwise. Leaves no trace (throwaway tmux + root).
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

SESSION="jagt-push-smoke-$$"
PORT=8299
ROOT="$(mktemp -d)"
TASK="ABC-1001"

cleanup() { tmux kill-session -t "$SESSION" 2>/dev/null; rm -rf "$ROOT"; }
trap cleanup EXIT

# refreshSeconds 60: far longer than this test runs, so the timer cannot be what repaints the screen.
printf 'orchestrator:\n  dashboard: {refreshSeconds: 60, reservedRows: 8}\n  viewer: {tmuxSession: "%s"}\n  projects: {}\n' "$SESSION" > "$ROOT/jagt.yml"
: > "$ROOT/mcp_client.js"
# printf, not a JSON library: the only tools this harness may assume are the ones jagt itself needs.
printf '{
  "tasks": {
    "%s": {
      "project": "demo",
      "worktreePath": "/tmp/wt/1",
      "status": "IN_PROGRESS",
      "lastActiveTimestamp": %d,
      "message": "working",
      "alias": "p1",
      "remoteUrl": "git@example.com:demo/demo.git",
      "title": "Fictional task 1"
    }
  }
}\n' "$TASK" "$(( $(date +%s) * 1000 ))" > "$ROOT/state.json"

tmux kill-session -t "$SESSION" 2>/dev/null
tmux new-session -d -s "$SESSION" -x 120 -y 30
tmux send-keys -t "$SESSION" "ORCHESTRATOR_ROOT=$ROOT $JAVA -jar $JAR --server.port=$PORT --orchestrator.ui=tui --orchestrator.open-warp-window=false --orchestrator.startup-checks=false" Enter
for _ in $(seq 1 40); do curl -s "localhost:$PORT/state" >/dev/null 2>&1 && break; sleep 1; done
sleep 3

# The screen carries TaskStatus.label(), not the enum name: what a human reads is the whole point of the
# dashboard, so this asserts what a human would see.
if ! tmux capture-pane -p -t "$SESSION" | grep -q 'agent working'; then
  echo "FAIL: the dashboard never showed the initial 'agent working' row — the console did not start"
  exit 1
fi

# SHIPPING on purpose: the statuses that notify the human (REVIEW_PENDING, CI_FAILED) would pop a desktop
# notification on the machine running the test.
curl -s -X POST "localhost:$PORT/mcp" -H 'Content-Type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":1,\
\"method\":\"tools/call\",\"params\":{\"name\":\"update_agent_status\",\"arguments\":{\"status\":\"SHIPPING\",\
\"message\":\"shipping\",\"taskId\":\"$TASK\"}}}" >/dev/null

for _ in $(seq 1 6); do
  sleep 1
  if tmux capture-pane -p -t "$SESSION" | grep -q 'pushing'; then
    echo "PASS: the dashboard showed the new status without waiting for the 60s refresh"
    exit 0
  fi
done

echo "FAIL: the status change never reached the screen — the console is still waiting for its timer"
tmux capture-pane -p -t "$SESSION" | tail -12
exit 1
