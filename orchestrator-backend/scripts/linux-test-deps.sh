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
    echo "  plus the shared libraries a headless Chromium links against (see the list below)"
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
    ca-certificates \
    fonts-liberation \
    libasound2t64 \
    libatk-bridge2.0-0t64 \
    libatk1.0-0t64 \
    libcairo2 \
    libcups2t64 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libglib2.0-0t64 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libx11-6 \
    libxcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2
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
#   fonts-liberation … libxrandr2 — the shared libraries the board suite's Chromium links against. The BROWSER
#     comes from Playwright's own cache (the `playwrightChromium` task), which cannot install system packages.
#     The t64 names are Ubuntu 24.04 and newer; on an older Debian they are the ones without the suffix.
