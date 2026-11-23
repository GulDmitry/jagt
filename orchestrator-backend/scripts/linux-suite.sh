#!/usr/bin/env bash
# Runs CI's Linux jobs on a REAL Linux, from a Mac, each in its own container: the line its ci.yml job runs, in an image
# carrying what a runner carries (docker/linux-suite.Dockerfile), with a Docker engine of its own as a runner has.
#
# Usage: scripts/linux-suite.sh <job…>      (jobs: test heavy stand install)
# Requires: Docker. Leaves nothing on the host but the Gradle cache, the Docker engine's volume and the image.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/orchestrator-backend"
IMAGE=jagt-linux-suite
CACHE_VOLUME=jagt-gradle-home
ENGINE_VOLUME=jagt-ci-docker

gate() {
    case "$1" in
        test) echo './gradlew --no-daemon gateTest' ;;
        heavy) echo 'scripts/with-linux-desktop.sh ./gradlew --no-daemon gateHeavy' ;;
        stand) echo './gradlew --no-daemon gateStand -Pmodelfree -Pengine=compose' ;;
        install) echo './gradlew --no-daemon gateInstall' ;;
        *) echo "linux-suite: no job $1; jobs: test heavy stand install" >&2; return 2 ;;
    esac
}

[[ $# -gt 0 ]] || { gate usage; exit 2; }
for job in "$@"; do gate "$job" >/dev/null || exit 2; done
command -v docker >/dev/null || { echo "docker is required"; exit 2; }

echo "==> building $IMAGE"
docker build -q -f "$BACKEND_DIR/docker/linux-suite.Dockerfile" -t "$IMAGE" "$BACKEND_DIR" >/dev/null
docker volume create "$CACHE_VOLUME" >/dev/null
docker volume create "$ENGINE_VOLUME" >/dev/null

# Gradle's project state records absolute paths, so the container's own lives in anonymous volumes, never on the host;
# so do the board's Linux node_modules.
STATE_MOUNTS=(-v /jagt/board/node_modules)
for project in "$BACKEND_DIR"/build.gradle "$BACKEND_DIR"/*/build.gradle; do
    dir="/jagt/${project#"$REPO_ROOT"/}"
    dir="${dir%/build.gradle}"
    STATE_MOUNTS+=(-v "$dir/build" -v "$dir/.gradle")
done

# The engine starts as root; the job runs as the image's own user, uid 1000, as on a CI runner, since root ignores file
# modes. Only the volumes it writes are handed to it first.
# shellcheck disable=SC2016 # the container's shell expands it
AS_RUNNER='dockerd >/tmp/dockerd.log 2>&1 &
timeout 60 sh -c "until docker info >/dev/null 2>&1; do sleep 1; done" || { cat /tmp/dockerd.log; exit 1; }
chmod 666 /var/run/docker.sock
[[ $(stat -c %u /gradle-home) == 1000 ]] || chown -R 1000:1000 /gradle-home
chown 1000:1000 /jagt/board/node_modules /jagt/orchestrator-backend/build /jagt/orchestrator-backend/.gradle \
    /jagt/orchestrator-backend/*/build /jagt/orchestrator-backend/*/.gradle
exec setpriv --reuid=1000 --regid=1000 --init-groups env HOME="$(getent passwd 1000 | cut -d: -f6)" "$@"'

# A linked worktree's .git points at its main checkout's git dir by absolute path, so that dir is mounted there too.
GIT_MOUNTS=()
GIT_COMMON_DIR="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir)"
if [[ "$GIT_COMMON_DIR" != "$REPO_ROOT/.git" ]]; then GIT_MOUNTS=(-v "$GIT_COMMON_DIR:$GIT_COMMON_DIR"); fi

# Docker Desktop's VM can serve a file changed minutes ago from a stale page cache: git then fails to hash it.
if [[ $(uname -s) == Darwin ]]; then
    docker run --rm --privileged "$IMAGE" sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' >/dev/null
fi

status=0
for job in "$@"; do
    line=$(gate "$job")
    echo "==> $job: $line"
    # --init: a killed command's orphan is reaped, never left a zombie that still reads as alive.
    # shellcheck disable=SC2086 # the gate is a command line
    docker run --rm --init --privileged -e CI=true -e ORG_GRADLE_PROJECT_timed=false \
        -v "$REPO_ROOT:/jagt" \
        "${STATE_MOUNTS[@]}" \
        ${GIT_MOUNTS[@]+"${GIT_MOUNTS[@]}"} \
        -v "$CACHE_VOLUME:/gradle-home" \
        -v "$ENGINE_VOLUME:/var/lib/docker" \
        "$IMAGE" bash -c "$AS_RUNNER" - $line || { status=$?; echo "==> $job red (exit $status)"; }
done
exit "$status"
