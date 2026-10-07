## Why

A task split into two requests stalled on a branch only the human could push; in `act` the Master could not open it
([0015](../../../docs/decisions/0015-another-branch-is-another-task.md)).

## What Changes

- A session needing a second branch asks for it; the push guard says so.
- In `act` the Master opens it as a task of its own with a `do` line, through the human's own launch.

## Capabilities

### Modified Capabilities

- `master`: the Master opens a task in `act`.
- `task-flow`: a refused foreign push names the way out.
