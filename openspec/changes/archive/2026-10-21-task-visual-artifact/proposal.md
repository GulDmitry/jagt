## Why

A session's sketch of its change has nowhere to live, and reading `plan.md` or `task_notes.md` means opening the worktree
in an editor or a terminal.

## What Changes

- With `orchestrator.agent.visualize.enabled` on, the brief asks the session to write `task_visual.md` when the task is worth a picture.
- The open task gets a fourth tab, Files, listing the documents present in its worktree and showing one rendered.
- Files replaces opening the worktree in an editor or terminal just to read `plan.md` or `task_notes.md`.
- Autoplan moves from `task-flow`, past its page budget, to the new `session-brief` beside Visualize.

## Capabilities

### New Capabilities

- `session-brief`: what the brief asks of a session before its first edit: autoplan and visualize.

### Modified Capabilities

- `task-flow`: Autoplan leaves for `session-brief`.
- `board-workspace`: the open task has four tabs.
