#!/usr/bin/env bash
# Provides the desktop the Linux DRIVER tests need, then runs whatever it was given.
#
# One script for every environment that has to provide it — the container image, a GitHub Actions runner, a
# GitLab job, a Linux developer's headless box — because "start Xvfb, then a session bus, then a notification
# daemon, in that order, and wait for each" is exactly the kind of setup that gets copied into three CI files
# and then diverges. Nothing here is jagt-specific: it is the display, the bus and the daemon.
#
# Usage: scripts/with-linux-desktop.sh ./gradlew --no-daemon linuxDriverTest
set -euo pipefail

if [[ $# -eq 0 ]]; then
    echo "usage: with-linux-desktop.sh <command…>"; exit 2
fi
if [[ "$(uname -s)" != "Linux" ]]; then
    echo "with-linux-desktop: not Linux — running the command as-is (no display to provide)."
    exec "$@"
fi

DISPLAY_NUM="${JAGT_TEST_DISPLAY:-99}"
export DISPLAY=":$DISPLAY_NUM"

cleanup() {
    # Best effort: these are per-job processes, and a CI runner is thrown away anyway.
    [[ -n "${XVFB_PID:-}" ]] && kill "$XVFB_PID" 2>/dev/null
    [[ -n "${DUNST_PID:-}" ]] && kill "$DUNST_PID" 2>/dev/null
    [[ -n "${DBUS_SESSION_BUS_PID:-}" ]] && kill "$DBUS_SESSION_BUS_PID" 2>/dev/null
    return 0
}
trap cleanup EXIT

Xvfb "$DISPLAY" -screen 0 1280x800x24 >/tmp/jagt-xvfb.log 2>&1 &
XVFB_PID=$!
for _ in $(seq 1 60); do
    [[ -e "/tmp/.X11-unix/X$DISPLAY_NUM" ]] && break
    sleep 0.25
done
if [[ ! -e "/tmp/.X11-unix/X$DISPLAY_NUM" ]]; then
    echo "with-linux-desktop: Xvfb never came up on $DISPLAY (see /tmp/jagt-xvfb.log)"; exit 1
fi

# `eval` is how dbus-launch hands back DBUS_SESSION_BUS_ADDRESS/_PID — there is no other interface.
eval "$(dbus-launch --sh-syntax)"
export DBUS_SESSION_BUS_ADDRESS DBUS_SESSION_BUS_PID

dunst >/tmp/jagt-dunst.log 2>&1 &
DUNST_PID=$!
# The daemon has to own org.freedesktop.Notifications before the first notify-send, or that call exits 1.
for _ in $(seq 1 40); do
    if command -v dbus-send >/dev/null && dbus-send --session --dest=org.freedesktop.DBus --print-reply \
            --type=method_call /org/freedesktop/DBus org.freedesktop.DBus.ListNames 2>/dev/null \
            | grep -q "org.freedesktop.Notifications"; then
        break
    fi
    sleep 0.25
done

exec "$@"
