## Why
A call presenting the Master token from the root, outside any task worktree, was recorded as `mcp`, so the task's session turn read the root's move as its own answer.

## What Changes
- A step a root-token call took names `root`, not `mcp`; only a task session's own call names `mcp`.

## Capabilities

### Modified Capabilities

- `board-workspace`: the history names who asked.
