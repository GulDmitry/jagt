#!/usr/bin/env bash
# Runs CI's Linux jobs on a REAL Linux, from a Mac, each in its own container: the line its ci.yml job runs, in an image
# in the CI image ci.yml pins (base/Dockerfile.ci), with a Docker engine of its own as a runner has.
# While the pinned digest is not pullable, the image is built from base/Dockerfile.ci instead.
# The container is never capped in CPU or memory: the machine is the resource. Every job runs with its network, as on a runner;
# a Gradle home and npm cache volume only speeds it up, and an empty one never changes a result.
#
# Usage: scripts/linux-suite.sh <job…>      (jobs: test heavy stand stand-kind install audit codeql boardTest, and one per nightly.yml job: nightly-*)
# Requires: Docker. Leaves nothing on the host but the Gradle cache, the Docker engine's volume, the image and each job's
# test reports under build/linux-suite/<job>, where a workflow uploads them.
# exits: 0 2
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/orchestrator-backend"
IMAGE="$(grep -m1 -oE 'ghcr\.io/[^ ]+-ci:base@sha256:[0-9a-f]{64}' "$REPO_ROOT/.github/workflows/ci.yml" || true)"
LOCAL_IMAGE=jagt-ci:local
# One speed cache per job; two runs of a job take turns on it, as Gradle's locks do not cross containers.
# A path in JAGT_GRADLE_CACHE puts the speed cache on the host, where a CI cache action saves and restores it.
CACHE=${JAGT_GRADLE_CACHE:-jagt-gradle-home}
# A runner starts with an empty engine, so each run gets its own.
ENGINE_VOLUME="jagt-ci-docker-$$"

gate() {
    case "$1" in
        test) echo './gradlew --no-daemon gateTest' ;;
        heavy) echo 'scripts/with-linux-desktop.sh ./gradlew --no-daemon gateHeavy' ;;
        stand) echo './gradlew --no-daemon gateStand -Pmodelfree -Pengine=compose' ;;
        stand-kind) echo './gradlew --no-daemon gateStand -Pmodelfree -Pengine=kind' ;;
        install) echo './gradlew --no-daemon gateInstall' ;;
        audit) echo './gradlew --no-daemon gateScan -Paudit.base=origin/main' ;;
        codeql) echo 'scripts/codeql-local.sh' ;;
        boardTest) echo './gradlew --no-daemon boardInstall boardTest' ;;
        nightly-stand-compose) echo './gradlew --no-daemon gateStand -Pmodelfree -Pengine=compose -PstandRuns=3' ;;
        nightly-stand-kind) echo './gradlew --no-daemon gateStand -Pmodelfree -Pengine=kind -PstandRuns=3' ;;
        nightly-e2e) echo './gradlew --no-daemon gateRepeat' ;;
        nightly-properties) echo './gradlew --no-daemon gateProperties -PjqwikTries=1000 --rerun-tasks' ;;
        nightly-scan) echo './gradlew --no-daemon gateScan' ;;
        *) echo "linux-suite: no job $1; jobs: test heavy stand stand-kind install audit codeql boardTest nightly-*" >&2; return 2 ;;
    esac
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
docker volume create "$ENGINE_VOLUME" >/dev/null
trap 'docker volume rm -f "$ENGINE_VOLUME" >/dev/null 2>&1 || true' EXIT # absent is removed

# Gradle's project state records absolute paths, so the container's own lives in anonymous volumes, never on the host;
# so do the board's Linux node_modules.
STATE_MOUNTS=(-v /jagt/board/node_modules)
for project in "$BACKEND_DIR"/build.gradle "$BACKEND_DIR"/*/build.gradle; do
    dir="/jagt/${project#"$REPO_ROOT"/}"
    dir="${dir%/build.gradle}"
    STATE_MOUNTS+=(-v "$dir/build" -v "$dir/.gradle")
done

# The engine starts as root; a job runs as RUN_UID, as on a CI runner, since root ignores file modes. Only the volumes it
# writes are handed to it first. The cgroup block is moby's hack/dind with its retry loop: moving the root group's processes
# can race a new one, so the delegation repeats until it holds. One engine volume holds one engine, so a second container waits.
# Each project's test reports and the stand's evidence are copied from the container's own build volumes to the host's.
# shellcheck disable=SC2016 # the container's shell expands it
AS_RUNNER='exec 8>/gradle-home/.run.lock
flock 8
[[ ${KIND_TOOLS:-} != 1 ]] || bash /jagt/orchestrator-backend/scripts/linux-test-deps.sh --kind >/dev/null
exec 9>/var/lib/docker/.engine.lock
flock 9
if [[ -f /sys/fs/cgroup/cgroup.controllers ]]; then
    mkdir -p /sys/fs/cgroup/init
    while ! {
        xargs -rn1 </sys/fs/cgroup/cgroup.procs >/sys/fs/cgroup/init/cgroup.procs || :
        sed -e "s/ / +/g" -e "s/^/+/" </sys/fs/cgroup/cgroup.controllers >/sys/fs/cgroup/cgroup.subtree_control
    }; do
        true
    done
fi
dockerd >/tmp/dockerd.log 2>&1 &
timeout 60 sh -c "until docker info >/dev/null 2>&1; do sleep 1; done" || { cat /tmp/dockerd.log; exit 1; }
chmod 666 /var/run/docker.sock
[[ $(stat -c %u /gradle-home) == "$RUN_UID" ]] || chown -R "$RUN_UID:$RUN_UID" /gradle-home
chown "$RUN_UID:$RUN_UID" /jagt/board/node_modules /jagt/orchestrator-backend/build /jagt/orchestrator-backend/.gradle \
    /jagt/orchestrator-backend/*/build /jagt/orchestrator-backend/*/.gradle
getent passwd "$RUN_UID" >/dev/null || useradd -u "$RUN_UID" -U -m jagt-runner
setpriv --reuid="$RUN_UID" --regid="$RUN_UID" --init-groups env HOME="$(getent passwd "$RUN_UID" | cut -d: -f6)" "$@" && rc=0 || rc=$?
for build in /jagt/orchestrator-backend/build /jagt/orchestrator-backend/*/build; do
    for kept in test-results reports/tests stand/failed; do
        [[ ! -d $build/$kept ]] || {
            to="/evidence/${build#/jagt/orchestrator-backend/}/$kept"
            mkdir -p "$(dirname "$to")" && cp -a "$build/$kept" "$to"; }
    done
done
for log in /tmp/jagt-*.log; do [[ ! -f $log ]] || cp "$log" /evidence/; done
chown -R "$RUN_UID:$RUN_UID" /evidence
exit $rc'

# On Linux a bind mount keeps the host's owner, so the job runs as the checkout's; Docker on a Mac maps it to anyone.
RUN_UID=1001 # a hosted runner's, unknown to the image
if [[ $(uname -s) == Linux ]]; then RUN_UID=$(stat -c %u "$REPO_ROOT"); fi
# The kind engine's tools are installed in its own run alone, never baked into the scanned image.
kind_tools() { if [[ $1 == *stand-kind ]]; then echo 1; else echo 0; fi; }

# A linked worktree's .git points at its main checkout's git dir by absolute path, so that dir is mounted there too.
GIT_MOUNTS=()
GIT_COMMON_DIR="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir)"
if [[ "$GIT_COMMON_DIR" != "$REPO_ROOT/.git" ]]; then GIT_MOUNTS=(-v "$GIT_COMMON_DIR:$GIT_COMMON_DIR"); fi

# Docker Desktop's VM can serve a file changed minutes ago from a stale page cache: git then fails to hash it.
if [[ $(uname -s) == Darwin ]]; then
    docker run --rm --privileged "$IMAGE" sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' >/dev/null
fi

# --init: a killed command's orphan is reaped, never left a zombie that still reads as alive.
COMMON=(--rm --init --privileged -e CI=true -e ORG_GRADLE_PROJECT_timed=false
    -w /jagt/orchestrator-backend -v "$REPO_ROOT:/jagt" "${STATE_MOUNTS[@]}" ${GIT_MOUNTS[@]+"${GIT_MOUNTS[@]}"}
    -v "$ENGINE_VOLUME:/var/lib/docker")

status=0
for job in "$@"; do
    line=$(gate "$job")
    cache=$CACHE
    if [[ $CACHE == /* ]]; then mkdir -p "$cache"; else cache="$CACHE-$job"; docker volume create "$cache" >/dev/null; fi
    evidence="$BACKEND_DIR/build/linux-suite/$job"
    rm -rf "$evidence" && mkdir -p "$evidence"
    echo "==> $job: $line"
    # shellcheck disable=SC2086 # the gate is a command line
    docker run "${COMMON[@]}" -e RUN_UID="$RUN_UID" -e KIND_TOOLS="$(kind_tools "$job")" -e GRADLE_USER_HOME=/gradle-home \
        -e PLAYWRIGHT_BROWSERS_PATH=/gradle-home/ms-playwright -e npm_config_cache=/gradle-home/npm-cache \
        -v "$cache:/gradle-home" -v "$evidence:/evidence" "$IMAGE" bash -c "$AS_RUNNER" - $line || { status=$?; echo "==> $job red (exit $status)"; }
done
exit "$status"
