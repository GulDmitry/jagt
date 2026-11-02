## Why

A session opened the CLI's question dialog after reporting its question; the Master's relayed answer waited behind the
dialog and never reached it. Only the session's own notes can carry a question that either party may answer.

## What Changes

- The worktree settings deny `AskUserQuestion`.
- The brief has the session write question, options and pick in `task_notes.md`, report, and end its turn.

## Capabilities

### Modified Capabilities

- `attention`: a question's options and pick are in `task_notes.md` before the report; the dialog is refused.
- `runtime`: the worktree settings deny the question dialog.
