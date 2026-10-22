#!/bin/sh
# tmux falls back to /tmp when $TMUX_TMPDIR does not exist, so the socket is named outright.
tmux -S "$TMUX_TMPDIR/tmux-$(id -u)/default" kill-server 2>/dev/null
rm -rf "$TMUX_TMPDIR"
true
