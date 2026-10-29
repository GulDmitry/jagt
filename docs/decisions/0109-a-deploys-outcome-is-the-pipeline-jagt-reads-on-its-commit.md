# 0109 — A deploy's outcome is the pipeline jagt reads on its commit

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md), [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-29. Extends [0013](0013-a-deploy-conflict-is-the-sessions-in-act.md) and [0105](0105-infrastructure-failures-are-waited-out.md).

## Context

- A resolved deploy conflict landed on the deploy branch and its pipeline failed: an import left unused only in the merge result.
- `DeployCheckJob` asked the session, which went `IN_PROGRESS` in its own worktree, where a merge-only defect does not exist.
- Only `deploy` and `revert` write the deploy branch, so nothing let the session fix it there; the human did, by hand.

## Decision

- A deploy is settled when the host's pipeline on each repository's `deployCommit` is green, read by jagt through the host's own CLI
  (`PipelineRetry.onCommit`), never by a model.
- Until then the deploy stays open: the session is not asked, and the status line says `waiting on its pipeline` with the repository and commit.
- `DeployCheckJob` reads it every `READ_EVERY` (1 minute), and only for a deploy not yet asked.
- A pipeline red on infrastructure follows [0105](0105-infrastructure-failures-are-waited-out.md): `RedCauses.judgeDeploy` arms the same retry,
  `InfraRetryJob` sends it, and the last wait stops the task as `INFRA`.
- A pipeline red on the code hands the deploy worktree to the session as [0013](0013-a-deploy-conflict-is-the-sessions-in-act.md) hands a conflict:
  `DEPLOY_CONFLICT`, the worktree cut from the deploy branch, the status line naming `red pipeline on the deploy commit`.
- The session fixes the merge result there and stages it, as for a conflict; jagt commits and pushes it through the deploy path, then reads again.
- In `judge` the card says NEEDS YOU, as for a conflict; nothing is pressed.
- A press on a fix worktree with nothing staged is refused and keeps it: the deploy waits on the fix.
- A pipeline that is unknown or cannot be read keeps the deploy open, and ends on the next successful read.
- No pipeline settles the deploy where none can come: the host lists none `NO_PIPELINE_AFTER` (10 minutes) after the push,
  or the repository has no host address.
- `deployCommit` stays the merge `revert` takes out; later fixes are `deployFixes`, and `TaskRepo.deployTip` is what the pipeline is read on.
- `revert` takes the fixes out newest first, then the merge, in one push: a plain commit added on top, no force-push, no guard weakened.
- `RedCause.of` is the one place deciding what is red on the code; `DeployCheckJob.READ_EVERY` the one read interval.
- A read of the review request no longer clears the retries of a task past its review (`RedCauses.judge`): the deploy owns them there.

## Rejected

- Asking the session after a red: its worktree has no merge, so the defect is invisible to it.
- The session pushing the fix: only `deploy` and `revert` write the deploy branch ([`guards`](../rules/guards.md)).
- A new status for a red deploy: `DEPLOY_CONFLICT` already means the deploy waits in the deploy worktree.
- Reading the branch's tip: another task's commit would settle this one.

## Reopen when

- A pipeline arrives later than `NO_PIPELINE_AFTER` on a project and a red deploy goes unseen: raise it per project, not for all.
