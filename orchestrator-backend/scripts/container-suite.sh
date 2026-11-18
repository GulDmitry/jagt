#!/usr/bin/env bash
# jagt's own image, as CI and a laptop run it: build, start with compose and a stub project, log in by password,
# drive one boardTest scenario against the container's URL. The server scenarios are the stand's (standTest).
# Needs docker, java and Chromium's libraries (scripts/linux-test-deps.sh). Leaves no container, volume, network or image.
# Usage: scripts/container-suite.sh      (from any directory)
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$BACKEND_DIR/.." && pwd)"
PROJECT=jagt-container-suite
PORT=${JAGT_PORT:-18290}
PASSWORD=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')

command -v docker >/dev/null || { echo "docker is required"; exit 2; }

FIXTURES=$(mktemp -d)
compose() {
    JAGT_PORT=$PORT JAGT_JAR_DIR="$BACKEND_DIR/build/libs" JAGT_FIXTURES=$FIXTURES JAGT_STUB="$BACKEND_DIR/scripts/container-suite" \
        docker compose -p "$PROJECT" -f "$REPO_ROOT/docker-compose.yml" \
        -f "$BACKEND_DIR/scripts/container-suite/compose.test.yml" "$@"
}
cleanup() {
    status=$?
    [ "$status" -eq 0 ] || compose logs --no-color --tail 200 || true
    compose down --volumes --rmi local >/dev/null 2>&1 || true
    rm -rf "$FIXTURES"
}
trap cleanup EXIT

hash=$(docker run --rm httpd:2-alpine htpasswd -nbBC 10 "" "$PASSWORD" | tr -d ':\n')
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

echo "==> building the jar and starting the image"
(cd "$BACKEND_DIR" && ./gradlew --no-daemon bootJar -x test)
compose up --detach --build
for _ in $(seq 1 90); do
    curl -fsS "http://localhost:$PORT/actuator/health/readiness" >/dev/null 2>&1 && break
    sleep 2
done
curl -fsS "http://localhost:$PORT/actuator/health/readiness" >/dev/null

echo "==> boardTest against the container"
cd "$BACKEND_DIR"
JAGT_BOARD_URL="http://localhost:$PORT" JAGT_BOARD_PASSWORD="$PASSWORD" \
    ./gradlew --no-daemon boardTest --tests '*BoardContainerTest'

compose down --volumes --rmi local >/dev/null
