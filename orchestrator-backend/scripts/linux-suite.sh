#!/usr/bin/env bash
# Runs jagt's suites on a REAL Linux, from a Mac — the "Linux runner" the roadmap asks for, in a container.
#
# WHY this exists: every Linux-specific piece (the `notify-send` notifier, the kitty driver, tmux windows on
# another kernel, git on overlayfs, the jar on a Linux JVM) was written and wired from macOS. A container IS a
# real Linux, so these are answerable without a second machine. The first run of it already found a shipped
# bug: `tmux-command` defaulted to /opt/homebrew/bin/tmux, so EVERY task on Linux died before its agent
# started.
#
# What it runs, in order:
#   1. `test`            — the hermetic unit suite, on a Linux JVM (Java 25, aarch64 or x86_64)
#   2. `e2eTest`         — the task-flow matrix with real git + real tmux (GUI drivers are doubles there)
#   3. `linuxDriverTest` — the Linux DRIVERS against real binaries: notify-send over a session D-Bus with a
#                          notification daemon, and kitty on an Xvfb display answering remote control
#
# What it CANNOT answer, and therefore does not pretend to: IntelliJ (`idea`), the macOS AppleScript raise,
# the Warp URI scheme, the real `claude` CLI, a live code host or tracker.
#
# Usage: scripts/linux-suite.sh [gradle-task…]      (default: the three above)
# Requires: Docker. Leaves nothing on the host but a Gradle cache volume and the image.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/orchestrator-backend"
IMAGE=jagt-linux-suite
CACHE_VOLUME=jagt-gradle-home
TASKS=("$@")
if [[ ${#TASKS[@]} -eq 0 ]]; then TASKS=(test e2eTest linuxDriverTest); fi

command -v docker >/dev/null || { echo "docker is required"; exit 2; }

echo "==> building $IMAGE"
docker build -q -f "$BACKEND_DIR/docker/linux-suite.Dockerfile" -t "$IMAGE" "$BACKEND_DIR" >/dev/null
docker volume create "$CACHE_VOLUME" >/dev/null

# The display, the session bus and the notification daemon come from with-linux-desktop.sh — the same script
# a CI job runs, so what is verified here is what CI verifies.
echo "==> running: ${TASKS[*]}"
docker run --rm \
    -v "$REPO_ROOT:/jagt" \
    -v "$CACHE_VOLUME:/gradle-home" \
    "$IMAGE" scripts/with-linux-desktop.sh ./gradlew --no-daemon "${TASKS[@]}"
echo "==> done"
