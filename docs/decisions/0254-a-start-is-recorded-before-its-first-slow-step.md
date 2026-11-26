# 0254 — A start is recorded before its first slow step

[← decisions](README.md) · spec: [`task-start`](../../openspec/specs/task-start/spec.md) ·
kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- A task exists only once its worktree is cut and its state written; the ticket read and the branch naming come first and take minutes.
- A restart in that window left no card, no file and no log line: the human's instructions were gone.
- The task's id is unknown before the read, `TaskState` holds no instructions and `NEW` means a running session.

## Decision

- **`TaskLauncher` records each start in `pending-starts.json` beside `state.json`** before any read or cut: key, request, start time.
  It deletes the record once the task's state is written and on every refusal.
- **The first read of the record logs `task start interrupted` per leftover**, no startup listener.
  It stays until `resume <key>`, `resume <key> discard` or a real task of that key.
- **The board's head shows one broken mark** naming each leftover with its words in the hover; it replaces nothing on a card.
- **`resume <key>` and `resume <key> discard` extend the existing `resume` command**, so no ceiling moves; the Master never presses it.
- **No `TaskStatus` and nothing in `flow/`**: a start that never became a task is the launcher's, not the chart's.

## Rejected

- A `TaskState` written first: no id yet, no place for the words, and `NEW` would claim a session.
- Two new commands: each is a `public-surface` row.
- One record per task folder: the folder is cut after the slow step.

## Reopen when

- A task is made from a path that bypasses `TaskLauncher`: its start would be unrecorded.
- A crash between the state write and the record's deletion shows a duplicate mark: the mark then needs the task id, not the key.
