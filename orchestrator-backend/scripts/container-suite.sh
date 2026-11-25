#!/usr/bin/env bash
# jagt's own image, as CI and a laptop run it: `up` builds it from build/libs, starts it with compose and a stub project,
# checks a first start creates TMPDIR on an empty volume and jagt.yml there survives a recreate,
# and writes the board's URL and password to build/container-suite/board.env;
# `down` removes every container, volume, network and image `up` made. The Gradle task boardContainerTest runs between them.
# Needs docker. Usage: scripts/container-suite.sh up|down      (from any directory)
# shellcheck disable=SC2016 # the config template and the image's shell expand their own variables
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$BACKEND_DIR/.." && pwd)"
PROJECT=jagt-container-suite
PORT=${JAGT_PORT:-18290}
FIXTURES=$BACKEND_DIR/build/container-suite
VOLUME=$PROJECT-config

command -v docker >/dev/null || { echo "docker is required"; exit 2; }

compose() {
    JAGT_PORT=$PORT JAGT_JAR_DIR="$BACKEND_DIR/build/libs" JAGT_FIXTURES=$FIXTURES JAGT_STUB="$BACKEND_DIR/scripts/container-suite" \
        docker compose -p "$PROJECT" -f "$REPO_ROOT/docker-compose.yml" \
        -f "$BACKEND_DIR/scripts/container-suite/compose.test.yml" "$@"
}

down() {
    compose down --volumes --rmi local >/dev/null 2>&1 || true # absent is removed
    docker volume rm -f "$VOLUME" >/dev/null 2>&1 || true # absent is removed
    rm -rf "$FIXTURES"
}

up() {
    trap 'compose logs --no-color --tail 200 || true' ERR # the failing status is kept
    down
    mkdir -p "$FIXTURES"
    password=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
    hash=$(docker run --rm httpd:2-alpine htpasswd -nbBC 10 "" "$password" | tr -d ':\n')
    printf '%s' "$hash" >"$FIXTURES/password-hash"
    printf 'JAGT_PUBLIC_URL=http://localhost:%s\nJAGT__orchestrator__projects__my-project__url=/fixtures/origin.git\nJAGT__orchestrator__roles__master__mode=off\n' \
        "$PORT" >"$FIXTURES/env"
    sed 's|${JAGT_PASSWORD_HASH}|${file:/run/secrets/jagt_password_hash}|' "$REPO_ROOT/jagt.server.yml.dist" >"$FIXTURES/jagt.yml"
    (
        cd "$FIXTURES"
        git init -q -b main work
        echo hi >work/a.txt
        git -C work add a.txt
        git -C work -c user.name=fixture -c user.email=fixture@localhost commit -qm init
        git clone -q --bare work origin.git
    )
    chmod -R a+rX "$FIXTURES"

    compose up --detach --build
    for _ in $(seq 1 90); do
        curl -fsS "http://localhost:$PORT/actuator/health/readiness" >/dev/null 2>&1 && break
        sleep 2
    done
    curl -fsS "http://localhost:$PORT/actuator/health/readiness" >/dev/null

    image=$(compose images --quiet jagt)
    docker volume create "$VOLUME" >/dev/null
    start() { docker run --rm -v "$VOLUME":/data "$@" -e JAGT_JAVA_OPTIONS=-version --entrypoint /app/entrypoint.sh "$image" >/dev/null 2>&1; }
    inside() { docker run --rm -v "$VOLUME":/data --entrypoint sh "$image" -c "$1"; }
    start -e TMPDIR=/data/tmp -e XDG_CACHE_HOME=/data/cache
    inside 'test -d /data/tmp && test -d /data/cache'
    inside 'echo "# added from the board" >>"$ORCHESTRATOR_CONFIG_FILE"'
    start
    inside 'grep -q "added from the board" "$ORCHESTRATOR_CONFIG_FILE"'

    printf 'JAGT_BOARD_URL=http://localhost:%s\nJAGT_BOARD_PASSWORD=%s\n' "$PORT" "$password" >"$FIXTURES/board.env"
}

case "${1:-}" in
    up) up ;;
    down) down ;;
    *) echo "usage: container-suite.sh up|down" >&2; exit 2 ;;
esac
