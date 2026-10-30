## Why

A deploy handed back from a conflict was found again by asking git which repository holds a deploy worktree, so a
worktree deleted by hand made the next press merge the first repository again.

## What Changes

- Where a deploy waits is read from the task's `Deployment`; a task without a recorded conflict is read as before.
- A press whose recorded conflict has lost its deploy worktree is refused, naming it.

## Capabilities

### Modified Capabilities

- `git`: the next `deploy` after a conflict resumes where the record says, or is refused naming a deleted worktree.
