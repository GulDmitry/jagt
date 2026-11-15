## Why

jagt wrote state, logs and worktrees into the launch directory and beside each repository, so a server could not keep it all on one volume and a desktop scattered it.

## What Changes

- One data folder, `orchestrator.data`, default `~/.jagt`, is the parent of `state.json` and its siblings, `worktrees/<project>/<task>`, `repos/<name>` and `logs/`.
- A task's worktree is cut under `worktrees/`, a project's own path is used in place.
- A `state.json` left in the old place is moved into the data folder once, at start.
- A folder jagt cannot write in is refused at start.

## Impact

- `startup`: gains "One data folder holds everything jagt writes".
