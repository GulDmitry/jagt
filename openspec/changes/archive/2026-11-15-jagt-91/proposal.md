## Why

In a container no human installs a fresh worktree's dependencies, so a session spends its first turns on the environment.

## What Changes

- A project's `setupCommand` (an argv list, `{branch}` and `{worktree}` placeholders) runs once in every new worktree of that project, after `worktree.copyGlobs` and before the session starts, under `setupMinutes` (default 20).
- A non-zero exit or the limit fails the start, naming the project, the exit and the output's tail; no session starts.
- Unset, nothing runs.

## Impact

- `task-start`: gains "A project's setup command runs in every new worktree".
