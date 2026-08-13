#!/usr/bin/env bash
# Everything the Linux suites shell out to, in ONE list — the container image and every CI runner install the
# same thing from here. Two lists would drift, and the failure that follows ("Failed to start command" on a
# machine nobody can reproduce) is expensive to read.
#
# Usage: sudo scripts/linux-test-deps.sh            (Debian/Ubuntu; a no-op elsewhere is deliberate)
set -euo pipefail

if ! command -v apt-get >/dev/null; then
    echo "linux-test-deps: no apt-get here — install the equivalents by hand:"
    echo "  git tmux nodejs kitty xvfb dbus-x11 dunst libnotify-bin procps lsof"
    exit 0
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
    git \
    tmux \
    nodejs \
    kitty \
    xvfb \
    dbus-x11 \
    dunst \
    libnotify-bin \
    procps \
    lsof \
    ca-certificates
rm -rf /var/lib/apt/lists/*

# Why each one is here, so nobody trims the list by guessing:
#   git, tmux  — the task flow itself runs them (worktrees, agent windows)
#   nodejs     — the MCP proxy every worktree symlinks
#   kitty      — the Linux TerminalDriver, tested against the real binary
#   xvfb       — kitty is a GUI application even when nobody is looking at it
#   dbus-x11   — a session bus, which is how a notification reaches a desktop
#   dunst      — a notification daemon: notify-send exits non-zero when nothing owns the bus name
#   libnotify-bin — notify-send itself, what LibNotifyNotifier runs
#   procps, lsof  — agent liveness probing and language-server reaping read the process table
