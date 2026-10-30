# 0114 — A revert conflict goes to the session

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md), [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-30. Reverses `GitDeploy`'s "a revert that conflicts is aborted and cleaned up, there being nothing
useful for a human to finish there"; extends [0013](0013-a-deploy-conflict-is-the-sessions-in-act.md) to it.

## Context

- A revert undoes a deploy on a shared branch; later work on that branch can touch what it takes out.
- The abort left the human a by-hand recipe, while the session that wrote the code was the one able to decide what survives.
- A deploy conflict already had the whole path: a worktree kept with its markers, a record, a hand-off, a finish.

## Decision

- A conflicting revert keeps its worktree (`<task>-revert`); the task is DEPLOY_CONFLICT, its `Conflict` of cause `REVERT`.
- `DeployConflicts`, `ConflictHandOff` and `ConflictFinish` find it from the record and that worktree, as a merge conflict.
- The next `revert` runs `git revert --continue` there, reverts what is left, pushes, and goes on to the remaining repositories.
- `revert` is refused while paths are unmerged; `deploy` is refused at a `REVERT` cause, so a deploy never finishes a revert's worktree.
- `revert` replaces `deploy` as the card's primary button at that cause: the hint, the dashboard line and the IDE open say so. No mark, no legend row.
- `ConflictFinish` presses `revert` as it presses `deploy`: act mode, the same stop and move guards, the same refusals and logs.

## Rejected

- Keeping the abort: the human decides what survives with less context than the session.
- A separate status for a revert conflict: DEPLOY_CONFLICT already means "a shared-branch write waits on a resolution".
- Letting `deploy` finish it: it would merge the task again over a half-made revert.

## Reopen when

- A resolved revert conflict pushed a revert the human did not want, so the finish needs a human press.
