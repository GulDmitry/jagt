# 0058 — A test never reaches the developer's terminal

[← decisions](README.md) · rule: [`docs/rules/testing.md`](../rules/testing.md)

## Status

Accepted 2026-10-22.

## Context

- On 2026-10-22 the `<test>StopTmux` finalizer ran a bare `tmux kill-server` while its `$TMUX_TMPDIR` was missing, the test task being up to date.
- tmux 3.8 fell back to `/tmp`, and the developer's live server, every agent session in it, died, twice.

## Decision

- The gate's test JVMs find no tmux and no kitty: `orchestrator.tmux-command` and `kitty-command` name binaries that do not exist.
- `KITTY_*` is removed from the test environment.
- A test-run tmux server is stopped only by its named socket (`gradle/stop-test-tmux.sh`, `tmux -S`).
- A gate test never reaches a terminal; a test needing a real one belongs in a named suite.

## Rejected

- `TMUX_TMPDIR` alone: tmux falls back silently when it is missing.
- A `[ -d ]` guard alone: the test JVM shares the same fallback.

## Reopen when

- A gate test genuinely needs a real terminal; it then moves to a named suite instead.
