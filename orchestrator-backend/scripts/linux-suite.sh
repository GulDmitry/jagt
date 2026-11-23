#!/usr/bin/env bash
# Runs CI's Linux jobs on a REAL Linux, from a Mac, each in its own container: the line its ci.yml job runs, in an image
# in the CI image ci.yml pins (base/Dockerfile.ci), with a Docker engine of its own as a runner has.
# While the pinned digest is not pullable, the image is built from base/Dockerfile.ci instead.
# The container is capped to a runner's size (runner.env). Each job first warms up online, resolving every dependency
# into a shared cache, then runs with no network: that cache is mounted read-only (GRADLE_RO_DEP_CACHE) under a Gradle
# home of the run's own, so two suites at once never meet on one lock.
#
# Usage: scripts/linux-suite.sh <job…>      (jobs: test heavy stand install audit boardTest)
# Requires: Docker. Leaves nothing on the host but the Gradle cache, the Docker engine's volume and the image.
# exits: 0 2
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/orchestrator-backend"
IMAGE="$(grep -m1 -oE 'ghcr\.io/[^ ]+-ci:base@sha256:[0-9a-f]{64}' "$REPO_ROOT/.github/workflows/ci.yml" || true)"
LOCAL_IMAGE=jagt-ci:local
CACHE_VOLUME=jagt-gradle-home
ENGINE_VOLUME=jagt-ci-docker
RUN_VOLUME="jagt-gradle-run-$$"
# shellcheck source=runner.env
. "$BACKEND_DIR/scripts/runner.env"

gate() {
    case "$1" in
        test) echo './gradlew --no-daemon gateTest' ;;
        heavy) echo 'scripts/with-linux-desktop.sh ./gradlew --no-daemon gateHeavy' ;;
        stand) echo './gradlew --no-daemon gateStand -Pmodelfree -Pengine=compose' ;;
        install) echo './gradlew --no-daemon gateInstall' ;;
        audit) echo './gradlew --no-daemon gateScan -Paudit.base=origin/main' ;;
        boardTest) echo './gradlew --no-daemon boardInstall boardTest' ;;
        *) echo "linux-suite: no job $1; jobs: test heavy stand install audit boardTest" >&2; return 2 ;;
    esac
}

# The steps of a job that read live data or install a tool: they run in the warm-up, online, and are left out offline.
live() {
    case "$1" in
        test) echo 'secretScan boardCheck' ;;
        heavy) echo 'playwrightChromium' ;;
        *) echo '' ;;
    esac
}

# What a job needs online besides every dependency: the board's npm cache and the images its stack builds.
warm() {
    case "$1" in
        test) echo "$(live test) boardInstall" ;;
        heavy) echo "$(live heavy) boardInstall containerUp containerDown" ;;
        stand) echo ':stand:standImage' ;;
        boardTest) echo 'playwrightChromium' ;;
        *) echo '' ;;
    esac
}

# The installer downloads the Java runtime from its public feed, which nothing here can cache; the audit is the live scan.
network() {
    case "$1" in
        install | audit) echo '' ;;
        *) echo '--network none --hostname runner --add-host runner:127.0.0.1' ;;
    esac
}

offline_line() {
    local line excluded=''
    line=$(gate "$1")
    for task in $(live "$1"); do excluded="$excluded -x $task"; done
    echo "$line$excluded"
}

[[ $# -gt 0 ]] || { gate usage; exit 2; }
for job in "$@"; do gate "$job" >/dev/null || exit 2; done
command -v docker >/dev/null || { echo "docker is required"; exit 2; }

if [[ -n "$IMAGE" ]] && docker pull -q "$IMAGE" >/dev/null 2>&1; then
    echo "==> using $IMAGE"
else
    echo "==> the pinned CI image is absent or not pullable; building $LOCAL_IMAGE from base/Dockerfile.ci"
    docker build -q --build-context "scripts=$BACKEND_DIR/scripts" -f "$REPO_ROOT/base/Dockerfile.ci" \
        -t "$LOCAL_IMAGE" "$REPO_ROOT/base" >/dev/null
    IMAGE="$LOCAL_IMAGE"
fi
docker volume create "$CACHE_VOLUME" >/dev/null
docker volume create "$ENGINE_VOLUME" >/dev/null
docker volume create "$RUN_VOLUME" >/dev/null
trap 'docker volume rm -f "$RUN_VOLUME" >/dev/null 2>&1 || true' EXIT # absent is removed

host_cpus=$(docker info --format '{{.NCPU}}' 2>/dev/null || echo "$RUNNER_CPUS")
cpus=$(( host_cpus < RUNNER_CPUS ? host_cpus : RUNNER_CPUS ))
memory_gb=$RUNNER_MEMORY_GB
host_gb=$(( $(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0) / 1073741824 ))
if (( host_gb > 1 && host_gb <= RUNNER_MEMORY_GB )); then
    memory_gb=$(( host_gb - 1 ))
    echo "==> warning: Docker has ${host_gb} GB, a runner ${RUNNER_MEMORY_GB} GB; the suite is capped at ${memory_gb} GB" >&2
fi
RUNNER_MEMORY="${memory_gb}g"
SIZE=(--cpus "$cpus" --memory "$RUNNER_MEMORY" --memory-swap "$RUNNER_MEMORY")

# Gradle's project state records absolute paths, so the container's own lives in anonymous volumes, never on the host;
# so do the board's Linux node_modules.
STATE_MOUNTS=(-v /jagt/board/node_modules)
for project in "$BACKEND_DIR"/build.gradle "$BACKEND_DIR"/*/build.gradle; do
    dir="/jagt/${project#"$REPO_ROOT"/}"
    dir="${dir%/build.gradle}"
    STATE_MOUNTS+=(-v "$dir/build" -v "$dir/.gradle")
done

# The engine starts as root; the job runs as the image's own user, uid 1000, as on a CI runner, since root ignores file
# modes. Only the volumes it writes are handed to it first; a read-only cache seeds the run's own home with what Gradle
# and npm cannot read from a read-only one. One engine volume holds one engine, so a second suite waits on its lock.
# shellcheck disable=SC2016 # the container's shell expands it
AS_RUNNER='exec 9>/var/lib/docker/.engine.lock
flock 9
dockerd >/tmp/dockerd.log 2>&1 &
timeout 60 sh -c "until docker info >/dev/null 2>&1; do sleep 1; done" || { cat /tmp/dockerd.log; exit 1; }
chmod 666 /var/run/docker.sock
if [[ -d /gradle-ro ]]; then
    for seed in wrapper npm-cache; do [[ ! -d /gradle-ro/$seed ]] || cp -a /gradle-ro/$seed /gradle-home/$seed; done
fi
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

# --init: a killed command's orphan is reaped, never left a zombie that still reads as alive.
COMMON=(--rm --init --privileged "${SIZE[@]}" -e CI=true -e ORG_GRADLE_PROJECT_timed=false
    -w /jagt/orchestrator-backend -v "$REPO_ROOT:/jagt" "${STATE_MOUNTS[@]}" ${GIT_MOUNTS[@]+"${GIT_MOUNTS[@]}"}
    -v "$ENGINE_VOLUME:/var/lib/docker")

# Online, and one at a time per cache: the lock lives in the cache's own volume.
warm_up() {
    local tasks
    tasks=$(warm "$1")
    echo "==> $1 warm-up (online): resolveAll $tasks"
    # shellcheck disable=SC2086 # the tasks are words
    docker run "${COMMON[@]}" -e GRADLE_USER_HOME=/gradle-home -e PLAYWRIGHT_BROWSERS_PATH=/gradle-home/ms-playwright \
        -e npm_config_cache=/gradle-home/npm-cache -v "$CACHE_VOLUME:/gradle-home" \
        "$IMAGE" bash -c "$AS_RUNNER" - \
        flock /gradle-home/.warm-up.lock ./gradlew --no-daemon --no-configuration-cache -I scripts/resolve-all.init.gradle resolveAll $tasks
}

status=0
for job in "$@"; do
    line=$(offline_line "$job")
    warm_up "$job" || { status=$?; echo "==> $job warm-up red (exit $status)"; continue; }
    echo "==> $job ($cpus CPUs, $RUNNER_MEMORY): $line"
    # shellcheck disable=SC2086,SC2046 # the gate is a command line, the network a few options
    docker run "${COMMON[@]}" $(network "$job") -e GRADLE_USER_HOME=/gradle-home -e GRADLE_RO_DEP_CACHE=/gradle-ro/caches \
        -e PLAYWRIGHT_BROWSERS_PATH=/gradle-ro/ms-playwright -e npm_config_cache=/gradle-home/npm-cache \
        -e npm_config_offline=true -v "$RUN_VOLUME:/gradle-home" -v "$CACHE_VOLUME:/gradle-ro:ro" \
        "$IMAGE" bash -c "$AS_RUNNER" - $line || { status=$?; echo "==> $job red (exit $status)"; }
done
exit "$status"
