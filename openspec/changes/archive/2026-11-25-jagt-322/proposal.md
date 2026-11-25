## Why

A task whose spec delta lives in another repository starts with no worktree of it, so the session cannot commit the delta.

## What Changes

- A project's `companions` names projects every task started on it also works in: one worktree each, one request each.
- Startup refuses a companion that is not a project, naming the key.

## Impact

- `repo-attach`: gains "A project's companions join every task started on it".
