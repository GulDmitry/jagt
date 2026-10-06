# 0013 — A deploy conflict is the session's in `act`

[← decisions](README.md) · rule: [`flow.md`](../rules/flow.md)

## Status

Accepted 2026-10-06.

## Context

With `master.mode: act` and `mine: [deploy, revert]`, a deploy press hit a conflict and the card said NEEDS YOU:
nothing in the loop read DEPLOY_CONFLICT, so the human resolved merges the session had the context for.

## Decision

- In `act`, `DeployConflictJob` relays the deploy worktree to the task's session once per conflict.
- The trigger to finish is the worktree's state, not a model's word: nothing unmerged, nothing unstaged, the merge
  standing or committed (`GitService.deployResolved`). jagt then presses `deploy` as the Master.
- That press finishes the human's own, so `mine: [deploy]` does not hold it back; the next deploy stays theirs.

## Rejected

- A second human press after the session resolved: the authorisation was already given, the remainder is work.

## Reopen when

- A session stages a resolution before it is done and a broken merge lands.
