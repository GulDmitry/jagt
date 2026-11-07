# 0133 — A red deploy pipeline is re-run once

[← decisions](README.md) · rule: [`deploy-check`](../../openspec/specs/deploy-check/spec.md)
· amends [0063](0063-a-stalled-deploy-conflict-is-the-humans.md)

## Status

Accepted 2026-11-04.

## Context

- A deploy pipeline went red on jobs other tickets owned and one flaky spec; jagt handed the deploy worktree to the session as a conflict.
- The session found nothing of its own to fix and staged nothing. A red-pipeline conflict finishes only once a fix is staged,
  so it could not finish: after `conflictStallMinutes` the human was told, and nobody moved.
- No cheap deterministic read says whether a failed job lies in code the task touched.

## Decision

- A deploy pipeline red on the code is re-run once per pushed commit (`CodeHost.retryFailed`) before it is handed back.
- The re-run is recorded on the deployment's target (`retried`, the commit's sha) before it is sent, so a restart never sends it again;
  a fix pushed after it is a new commit and owes its own.
- A host that refuses the re-run hands the red back as it stands, with no second try.
- Every red is re-run, not only one on jobs the task never touched: no deterministic read of that exists, and a model never decides a retry.
- A red infrastructure retry (`InfraRetryJob`) is untouched; this one starts where it ends.
- A stall after a hand-back is still the human's ([0063](0063-a-stalled-deploy-conflict-is-the-humans.md)).

## Rejected

- Retrying only jobs whose path the task never changed: a job's path is not in what the host answers.
- Counting the re-run against `infraWait.backoff`: that budget is for the host's own infrastructure verdict.

## Reopen when

- The host lists a failed job's source path, so a re-run can be limited to jobs of code the task never touched.
- A host reads a re-run pipeline as still red for a poll after the re-run: the hand-back would then come before the new run.
