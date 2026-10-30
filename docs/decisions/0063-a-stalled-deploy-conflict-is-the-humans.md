# 0063 — A stalled deploy conflict is the human's

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-22. Adds to [0013](0013-a-deploy-conflict-is-the-sessions-in-act.md).
Superseded in part by [0112](0112-a-deploy-is-a-saga-record-advanced-by-one-runner.md): the hand-off lives on the deployment's conflicted target.

## Context

- A conflict was relayed into a session whose CLI refused every edit in the deploy worktree, outside its working
  directories; its brief listed only its own worktree, so the Master told it not to resolve.
- The task sat in DEPLOY_CONFLICT for 40 minutes with 7 paths unmerged, and nobody was told.
- The ask was kept in memory by its text: a second press brought the conflict back and the session never heard.

## Decision

- The deploy worktree is the session's: named in its brief, admitted by `AgentRuntime.admit` at spawn and at hand-off.
- Claude's `admit` rewrites `.claude/settings.local.json`, reread mid-session (not yet seen live): a path rule always, an
  additional directory and a sandbox write path once the directory exists.
- A hand-off is the `Conflict` of the deployment's conflicted target, new with each press
  ([0112](0112-a-deploy-is-a-saga-record-advanced-by-one-runner.md)).
- Progress is a digest of the worktree's status and diff, so an edit before `git add` counts.
- Unchanged for `conflictStallMinutes`, the human is told once; the card says why until the worktree moves.

## Rejected

- Restarting the session with `--add-dir`: it loses the conversation that knows both sides of the merge.
- Counting only unmerged and staged paths: the brief asks for one `git add` at the end, so a session at work looks
  stalled.
- A new status: the flow table keeps its fourteen; the stall is a fact on the task, as the loop stop is.

## Reopen when

- A session's CLI does not pick up the rewritten settings: then admit at spawn alone, or respawn with the grant.
