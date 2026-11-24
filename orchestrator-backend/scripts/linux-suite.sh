#!/usr/bin/env bash
# Runs CI's Linux jobs on a REAL Linux, from a Mac, each in its own container: the line its ci.yml job runs, in an image
# in the CI image ci.yml pins (base/Dockerfile.ci), with a Docker engine of its own as a runner has.
# While the pinned digest is not pullable, the image is built from base/Dockerfile.ci instead.
# The container gets a CPU quota calibrated against a runner's times, a runner's memory and its concurrency (runner.env). Each job first warms up online, resolving every dependency
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
# A path in JAGT_GRADLE_CACHE puts the shared cache on the host, where a CI cache action saves and restores it.
CACHE_VOLUME=${JAGT_GRADLE_CACHE:-jagt-gradle-home}
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
        heavy | boardTest) echo 'playwrightChromium' ;;
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
if [[ $CACHE_VOLUME == /* ]]; then mkdir -p "$CACHE_VOLUME"; else docker volume create "$CACHE_VOLUME" >/dev/null; fi
docker volume create "$ENGINE_VOLUME" >/dev/null
docker volume create "$RUN_VOLUME" >/dev/null
trap 'docker volume rm -f "$RUN_VOLUME" >/dev/null 2>&1 || true' EXIT # absent is removed

host_cpus=$(docker info --format '{{.NCPU}}' 2>/dev/null || echo "$RUNNER_CPUS")
cpus=$CONTAINER_CPUS
# A hosted runner is the size calibrated against, so it keeps every CPU it has.
if [[ ${GITHUB_ACTIONS:-} == true ]]; then cpus=$host_cpus; fi
if awk -v h="$host_cpus" -v q="$CONTAINER_CPUS" 'BEGIN { exit !(h < q) }'; then cpus=$host_cpus; fi
# Slower per CPU than a runner, never fewer things at once: concurrency follows the runner's size.
CONCURRENCY=(--max-workers="$RUNNER_CPUS" -PstandParallelism="$RUNNER_CPUS" -Pe2eForks=$(( RUNNER_CPUS / 2 ))
    -PpitestThreads=$(( RUNNER_CPUS / 2 )))
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

# The engine starts as root; a job runs as RUN_UID, as on a CI runner, since root ignores file
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
[[ $(stat -c %u /gradle-home) == "$RUN_UID" ]] || chown -R "$RUN_UID:$RUN_UID" /gradle-home
chown "$RUN_UID:$RUN_UID" /jagt/board/node_modules /jagt/orchestrator-backend/build /jagt/orchestrator-backend/.gradle \
    /jagt/orchestrator-backend/*/build /jagt/orchestrator-backend/*/.gradle
home=$(getent passwd "$RUN_UID" | cut -d: -f6)
if [[ -z $home ]]; then home=/tmp/runner-home; install -d -o "$RUN_UID" -g "$RUN_UID" "$home"; fi
exec setpriv --reuid="$RUN_UID" --regid="$RUN_UID" --clear-groups env HOME="$home" "$@"'

# On Linux a bind mount keeps the host's owner, so the job runs as the checkout's; Docker on a Mac maps it to anyone.
RUN_UID=1000
if [[ $(uname -s) == Linux ]]; then RUN_UID=$(stat -c %u "$REPO_ROOT"); fi
# ci.yml's test job runs in its container as root, so its twin does too.
job_uid() { if [[ $1 == test ]]; then echo 0; else echo "$RUN_UID"; fi; }

# A linked worktree's .git points at its main checkout's git dir by absolute path, so that dir is mounted there too.
GIT_MOUNTS=()
GIT_COMMON_DIR="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir)"
if [[ "$GIT_COMMON_DIR" != "$REPO_ROOT/.git" ]]; then GIT_MOUNTS=(-v "$GIT_COMMON_DIR:$GIT_COMMON_DIR"); fi

# Docker Desktop's VM can serve a file changed minutes ago from a stale page cache: git then fails to hash it.
if [[ $(uname -s) == Darwin ]]; then
    docker run --rm --privileged "$IMAGE" sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' >/dev/null
fi

# --init: a killed command's orphan is reaped, never left a zombie that still reads as alive.
COMMON=(--rm --init --privileged "${SIZE[@]}" -e CI=true -e RUN_UID="$RUN_UID" -e ORG_GRADLE_PROJECT_timed=false
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
        flock /gradle-home/.warm-up.lock ./gradlew --no-daemon --no-configuration-cache -I scripts/resolve-all.init.gradle resolveAll $tasks "${CONCURRENCY[@]}"
}

status=0
for job in "$@"; do
    line=$(offline_line "$job")
    warm_up "$job" || { status=$?; echo "==> $job warm-up red (exit $status)"; continue; }
    echo "==> $job ($cpus CPUs, $RUNNER_MEMORY): $line"
    # shellcheck disable=SC2086,SC2046 # the gate is a command line, the network a few options
    docker run "${COMMON[@]}" $(network "$job") -e RUN_UID="$(job_uid "$job")" -e GRADLE_USER_HOME=/gradle-home -e GRADLE_RO_DEP_CACHE=/gradle-ro/caches \
        -e PLAYWRIGHT_BROWSERS_PATH=/gradle-ro/ms-playwright -e npm_config_cache=/gradle-home/npm-cache \
        -e npm_config_offline=true -v "$RUN_VOLUME:/gradle-home" -v "$CACHE_VOLUME:/gradle-ro:ro" \
        "$IMAGE" bash -c "$AS_RUNNER" - $line "${CONCURRENCY[@]}" || { status=$?; echo "==> $job red (exit $status)"; }
done
exit "$status"
