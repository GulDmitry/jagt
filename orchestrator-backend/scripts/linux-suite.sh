#!/usr/bin/env bash
# Runs jagt's suites on a REAL Linux, from a Mac, in a container.
#
# Every Linux-specific piece — the `notify-send` notifier, the kitty driver, tmux windows on another kernel,
# git on overlayfs, the jar on a Linux JVM — was written from macOS, and only a real Linux answers for it.
#
# What it runs, in order:
#   1. `test`            — the hermetic unit suite, on a Linux JVM (Java 25, aarch64 or x86_64)
#   2. `e2eTest`         — the task-flow matrix with real git + real tmux (GUI drivers are doubles there)
#   3. `boardTest`       — the web board in a headless Chromium (Playwright's own, downloaded on first run)
#   4. `linuxDriverTest` — the Linux DRIVERS against real binaries: notify-send over a session D-Bus with a
#                          notification daemon, and kitty on an Xvfb display answering remote control
#
# What it CANNOT answer, and therefore does not pretend to: IntelliJ (`idea`), the macOS AppleScript raise,
# the real agent CLI, a live code host or tracker.
#
# Usage: scripts/linux-suite.sh [gradle-task…]      (default: all four above)
# Requires: Docker. Leaves nothing on the host but a Gradle cache volume and the image.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/orchestrator-backend"
IMAGE=jagt-linux-suite
CACHE_VOLUME=jagt-gradle-home
TASKS=("$@")
if [[ ${#TASKS[@]} -eq 0 ]]; then TASKS=(test e2eTest boardTest linuxDriverTest); fi

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
