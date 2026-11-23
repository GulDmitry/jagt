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
#   5. `guardsTest`      — shellcheck and bats over the contributor guards in scripts/guards
#
# What it CANNOT answer, and therefore does not pretend to: a code viewer, the macOS AppleScript raise,
# the real agent CLI, a live code host or tracker.
#
# Usage: scripts/linux-suite.sh [gradle-task…]      (default: all five above)
# Requires: Docker. Leaves nothing on the host but a Gradle cache volume and the image.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/orchestrator-backend"
IMAGE=jagt-linux-suite
CACHE_VOLUME=jagt-gradle-home
TASKS=("$@")
if [[ ${#TASKS[@]} -eq 0 ]]; then TASKS=(test e2eTest boardTest linuxDriverTest guardsTest); fi

command -v docker >/dev/null || { echo "docker is required"; exit 2; }

echo "==> building $IMAGE"
docker build -q -f "$BACKEND_DIR/docker/linux-suite.Dockerfile" -t "$IMAGE" "$BACKEND_DIR" >/dev/null
docker volume create "$CACHE_VOLUME" >/dev/null

# The display, the session bus and the notification daemon come from with-linux-desktop.sh — the same script
# a CI job runs, so what is verified here is what CI verifies.
# Gradle's project state records absolute paths, so the container's own lives in anonymous volumes, never on the host.
STATE_MOUNTS=()
for project in "$BACKEND_DIR"/build.gradle "$BACKEND_DIR"/*/build.gradle; do
    dir="/jagt/${project#"$REPO_ROOT"/}"
    dir="${dir%/build.gradle}"
    STATE_MOUNTS+=(-v "$dir/build" -v "$dir/.gradle")
done

# Root ignores file modes, so the suites run as the image's own user, as they do on a CI runner;
# only the volumes it writes are handed to it first.
DROP_ROOT='[[ $(stat -c %u /gradle-home) == 1000 ]] || chown -R 1000:1000 /gradle-home
chown 1000:1000 /jagt/orchestrator-backend/build /jagt/orchestrator-backend/.gradle /jagt/orchestrator-backend/*/build /jagt/orchestrator-backend/*/.gradle
exec setpriv --reuid=1000 --regid=1000 --init-groups env HOME="$(getent passwd 1000 | cut -d: -f6)" "$@"' # the home of the image's own user, uid 1000

# A linked worktree's .git points at its main checkout's git dir by absolute path, so that dir is mounted there too.
GIT_MOUNTS=()
GIT_COMMON_DIR="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir)"
if [[ "$GIT_COMMON_DIR" != "$REPO_ROOT/.git" ]]; then GIT_MOUNTS=(-v "$GIT_COMMON_DIR:$GIT_COMMON_DIR"); fi

# Docker Desktop's VM can serve a file changed minutes ago from a stale page cache: git then fails to hash it.
if [[ $(uname -s) == Darwin ]]; then
    docker run --rm --privileged "$IMAGE" sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' >/dev/null
fi

echo "==> running: ${TASKS[*]}"
# --init: a killed command's orphan is reaped, never left a zombie that still reads as alive.
docker run --rm --init \
    -v "$REPO_ROOT:/jagt" \
    "${STATE_MOUNTS[@]}" \
    ${GIT_MOUNTS[@]+"${GIT_MOUNTS[@]}"} \
    -v "$CACHE_VOLUME:/gradle-home" \
    "$IMAGE" bash -c "$DROP_ROOT" - scripts/with-linux-desktop.sh ./gradlew --no-daemon "${TASKS[@]}"
echo "==> done"
