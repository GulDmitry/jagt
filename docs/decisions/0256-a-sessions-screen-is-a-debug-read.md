# 0256 — A session's screen is a debug read

[← decisions](README.md) · rule: [`debug-reads`](../../openspec/specs/debug-reads/spec.md) · on [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-26.

## Context

- What a session shows now was the one debug question still answered by `kubectl exec` and `tmux capture-pane`.
- `SessionHost.screenOf` already captures a task's pane; no new port method is needed.

## Decision

- **`read_screen` and `GET /api/debug/screen` answer from `AgentSessions.screen`**: colour codes removed, the last `lines`, no key sent.
- **The public surface rises by two**, 85 to 87 (`public-surface`, 0176): the tool and the endpoint.
- **What it replaces**: the `kubectl exec` and `tmux capture-pane` row of the server page.

## Rejected

- A new `SessionHost` method: `screenOf` is the same capture.
- A stream or an attach: the board's Session tab is the person's view.

## Reopen when

- A host has no pane to capture.
