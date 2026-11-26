#!/bin/bash
# Runs a command once one of N Gradle slots is free, so concurrent Gradle runs on one machine stay capped.
# A slot whose holder died is reclaimed; a call nested under a held slot runs at once, so a wrapper never holds two.
# One run per tree: a second call from a tree a live slot holds is refused, naming the holder's pid, as two runs
# rewrite the same build directories.
# Usage: gradle-slot.sh <command...>
# Env:   GRADLE_SLOTS=3, GRADLE_SLOT_WAIT=1800 (seconds), GRADLE_SLOT_POLL=5 (seconds), GRADLE_SLOT_DIR=/tmp/gradle-slots
# Exit:  the command's own, 75 when no slot freed within the wait, 76 when the tree already has a run.
# exits: 0 75 76
set -u
[ -n "${GRADLE_SLOT_HELD:-}" ] && exec "$@"
slots=${GRADLE_SLOTS:-3}
wait_max=${GRADLE_SLOT_WAIT:-1800}
poll=${GRADLE_SLOT_POLL:-5}
dir=${GRADLE_SLOT_DIR:-/tmp/gradle-slots}
mkdir -p "$dir"
tree=$(git rev-parse --show-toplevel 2>/dev/null || pwd -P)
start=$(date +%s)
while :; do
  for i in $(seq 1 "$slots"); do
    pid=$(cat "$dir/$i/pid" 2>/dev/null)
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then rm -rf "${dir:?}/$i"; fi
    if [ "$(cat "$dir/$i/tree" 2>/dev/null)" = "$tree" ]; then
      echo "gradle-slot: $tree already has a Gradle run (pid $(cat "$dir/$i/pid" 2>/dev/null)); end it first" >&2
      exit 76
    fi
  done
  for i in $(seq 1 "$slots"); do
    lock="$dir/$i"
    if mkdir "$lock" 2>/dev/null; then
      echo $$ > "$lock/pid"
      echo "$tree" > "$lock/tree"
      trap 'rm -rf "$lock"' EXIT INT TERM
      echo "gradle-slot: slot $i" >&2
      GRADLE_SLOT_HELD=$i "$@"
      exit $?
    fi
  done
  if (( $(date +%s) - start >= wait_max )); then
    echo "gradle-slot: no slot free after ${wait_max}s" >&2
    exit 75
  fi
  [ -n "${announced:-}" ] || echo "gradle-slot: waiting for a free slot (up to ${wait_max}s)" >&2
  announced=1
  sleep "$poll"
done
