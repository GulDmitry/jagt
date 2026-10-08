## Why

`remove_task` retired a task past `FlowEngine`, force-removing even an `IN_PROGRESS` worktree, while closing a task
stays the human's ([0016](../../../docs/decisions/0016-only-the-human-closes-a-task.md)). Nothing but that tool
needed it.

## What Changes

- `remove_task` is gone; a task is retired by the human's `done` alone.

## Capabilities

### Modified Capabilities

- `runtime`: `done` is the only verb that kills a session and reaps its worktree.
