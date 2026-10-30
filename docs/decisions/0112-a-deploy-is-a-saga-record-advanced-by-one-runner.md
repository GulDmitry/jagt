# 0112 — A deploy is a saga record, advanced by one runner

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md), [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-30. Extends [0013](0013-a-deploy-conflict-is-the-sessions-in-act.md), [0063](0063-a-stalled-deploy-conflict-is-the-humans.md),
[0109](0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md) and [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md).
Superseded in part by [0113](0113-the-deployment-record-is-the-only-deploy-store.md): the migration is complete.

## Context

- A deploy is ordered across repositories, yet its state is spread over `deployCommit` and `deployFixes` per repo, the status,
  `ConflictWatch`, the message and a deploy worktree on disk, each read by a different class.
- Nine defects in the 2026-10 deploy work were that spread: `46422829`, `1bd3b8da`, `3b83d415`, `69293ae5`, `5edd69a7`, `f4f79e25`,
  `cd4d1f7f`, `6e96395e`, `a83d2486`.
- A restart in the middle of a press must resume where it stopped, not from the repositories' fields.

## Decision

- A press is one stored `Deployment`: `pressedAt`, the ordered targets (project, deploy branch captured at the press, merge, fixes,
  `pushedAt`) and `at`, the first target not yet pushed. It lives on `TaskState.deployment`.
- The chart gains a stored `deploy` region over `DeployPhase`; the phase is derived from the record, as the stop region's is from the stop.
- Only `TaskReducer` writes the record, from a `DeployStep` carried by a `DeployStepped` move (`FlowDoorsTest`).
- Migration is six commits, each green: this one writes the record beside `deployCommit` and `deployFixes`, and nothing reads it.
  Later commits move each reader onto it, fold in the conflict watch, replace the loops by one runner, then delete the old fields.
- The first commit reaches `NONE`, `LANDING` and `HANDED`; `PIPELINE`, `SETTLED`, `CHECK_ASKED` and `REVERTING` come with their steps.
- `Standing` does not include the deploy region: a poll during a press must not refuse a relay
  ([0111](0111-a-relay-acts-only-where-the-task-still-stands.md)).
- The conflicted target holds a `Conflict`: cause, since, the progress and when it moved, the human told once, the stop holding it;
  leaving `DEPLOY_CONFLICT` clears it through the deploy region.
- A revert leaves the record as it is until the revert commit of the migration.

## Rejected

- Per-repo fields on `TaskRepo`: the saga is ordered across repos, and no one repo knows where the press stands.
- A derived region: a press must survive a restart, and nothing the host or git says rebuilds it.
- More guards on the existing fields: each defect above was a cell no guard named.

## Reopen when

- A press needs more than the targets' order and a phase to resume, or a second concurrent press per task.
