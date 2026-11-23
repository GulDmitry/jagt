#!/bin/bash
# Runs a command once one of N Gradle slots is free, so concurrent Gradle runs on one machine stay capped.
# A slot whose holder died is reclaimed; a call nested under a held slot runs at once, so a wrapper never holds two.
# Usage: gradle-slot.sh <command...>
# Env:   GRADLE_SLOTS=2, GRADLE_SLOT_WAIT=1800 (seconds), GRADLE_SLOT_POLL=5 (seconds), GRADLE_SLOT_DIR=/tmp/gradle-slots
# Exit:  the command's own, or 75 when no slot freed within the wait.
set -u
[ -n "${GRADLE_SLOT_HELD:-}" ] && exec "$@"
slots=${GRADLE_SLOTS:-2}
wait_max=${GRADLE_SLOT_WAIT:-1800}
poll=${GRADLE_SLOT_POLL:-5}
dir=${GRADLE_SLOT_DIR:-/tmp/gradle-slots}
mkdir -p "$dir"
start=$(date +%s)
while :; do
  for i in $(seq 1 "$slots"); do
    lock="$dir/$i"
    pid=$(cat "$lock/pid" 2>/dev/null)
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then rm -rf "$lock"; fi
    if mkdir "$lock" 2>/dev/null; then
      echo $$ > "$lock/pid"
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
  sleep "$poll"
done
