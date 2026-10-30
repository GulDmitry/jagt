## Why

A revert that conflicted was aborted and cleaned up, leaving the human a by-hand recipe: the session that knows the code never saw it.

## What Changes

- A conflicting revert keeps its revert worktree, and the task goes DEPLOY_CONFLICT with a conflict of cause `REVERT`.
- The session is handed it as a deploy conflict is; the next `revert` continues it and finishes the revert.
- At that conflict `revert` replaces `deploy` as the card's primary button; `deploy` is refused. No mark, no legend row.

## Capabilities

### Modified Capabilities

- `git`: a revert that conflicts goes to the session.
- `task-flow`: the Master finishes a resolved revert conflict as it finishes a deploy's.
