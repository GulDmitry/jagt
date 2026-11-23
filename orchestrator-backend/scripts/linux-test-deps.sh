#!/usr/bin/env bash
# Everything the Linux suites shell out to, in ONE list — the container image and every CI runner install the
# same thing from here. Two lists would drift, and the failure that follows ("Failed to start command" on a
# machine nobody can reproduce) is expensive to read.
#
# Usage: sudo scripts/linux-test-deps.sh            (Debian/Ubuntu; a no-op elsewhere is deliberate)
set -euo pipefail

if ! command -v apt-get >/dev/null; then
    echo "linux-test-deps: no apt-get here — install the equivalents by hand:"
    echo "  curl git tmux nodejs kitty xvfb fontconfig dbus-x11 dunst libnotify-bin procps lsof jq bats shellcheck"
    echo "  plus the shared libraries a headless Chromium links against (see the list below)"
    exit 0
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
    curl \
    git \
    tmux \
    nodejs \
    kitty \
    xvfb \
    fontconfig \
    dbus-x11 \
    dunst \
    libnotify-bin \
    procps \
    lsof \
    jq \
    shellcheck \
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
    libxrandr2 \
    libxtst6
rm -rf /var/lib/apt/lists/*

bats_version=1.14.0
bats_sha256=bb537b70b15b732f6d8827dd6578e3d8ce166636ce1f18ea9a074184fcce9177
bats_dir="$(mktemp -d)"
curl -fsSL -o "$bats_dir/bats.tgz" "https://github.com/bats-core/bats-core/archive/refs/tags/v$bats_version.tar.gz"
echo "$bats_sha256  $bats_dir/bats.tgz" | sha256sum -c -
tar xzf "$bats_dir/bats.tgz" -C "$bats_dir"
"$bats_dir/bats-core-$bats_version/install.sh" /usr/local
rm -rf "$bats_dir"

# Why each one is here, so nobody trims the list by guessing:
#   git, tmux  — the task flow itself runs them (worktrees, agent windows)
#   nodejs     — the MCP proxy every worktree symlinks
#   kitty      — the Linux TerminalDriver, tested against the real binary
#   xvfb       — kitty is a GUI application even when nobody is looking at it
#   fontconfig — kitty exits before opening a window without it ("Failed to find and load fontconfig") and
#     its own package dependencies do not bring it in; it also carries the fonts kitty then looks for
#   dbus-x11   — a session bus, which is how a notification reaches a desktop
#   dunst      — a notification daemon: notify-send exits non-zero when nothing owns the bus name
#   libnotify-bin — notify-send itself, what LibNotifyNotifier runs
#   procps, lsof  — agent liveness probing and language-server reaping read the process table
#   jq, bats, shellcheck — the contributor guards in scripts/guards and the guardsTest task that proves them;
#     bats pinned from its release, as the guards' bats_test_function --description needs 1.11 and noble ships 1.10
#   libxtst6   — on a display the JDK loads its X toolkit, which links against it; ImageIO reads the board's icons
#   fonts-liberation … libxrandr2 — the shared libraries the board suite's Chromium links against. The BROWSER
#     comes from Playwright's own cache (the `playwrightChromium` task), which cannot install system packages.
#     The t64 names are Ubuntu 24.04 and newer; on an older Debian they are the ones without the suffix.
