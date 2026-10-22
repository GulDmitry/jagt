# 0060 — The Session tab stays beside focus

[← decisions](README.md) · spec: [`board-workspace`](../../openspec/specs/board-workspace/spec.md)

## Status

Accepted 2026-10-22.

## Context

- `focus` raises the task's window, and re-enters a session that is down; the Session tab shows the same screen on the board.
- Removing the tab was proposed as two controls for one question.

## Decision

- The open task keeps its Session tab, last in the order.
- What `focus` cannot do: read one task's screen while tmux shows another task's window.

## Evidence

- The tab reads by window id (`TmuxSessionHost`, `capture-pane -t <window id>`), whichever window is on screen.
- Only the open task with the Session tab chosen is read, once every 5 s (`ui/tabs.js`); any other tab reads nothing.
- The read is a local tmux command: no model tokens, whatever the number of tasks.

## Rejected

- Removing the tab: it loses watching one session while working in another.

## Reopen when

- The board is reached from another machine than the windows', or `focus` gains a read-only peek.
- The human stops using the tab.
