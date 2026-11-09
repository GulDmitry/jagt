# 0154 — A deploy conflict names the reads that end it

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md),
[`deploy-check`](../../openspec/specs/deploy-check/spec.md)

## Status

Accepted 2026-11-09. Beside [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md), amends
[0013](0013-a-deploy-conflict-is-the-sessions-in-act.md) and [0109](0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md).

## Context

- `DEPLOY_CONFLICT` has three causes (`MERGE`, `RED_PIPELINE`, `REVERT`); a later green pipeline, a closed request and a
  staged fix end it, each read by its own job with its own watch: `DeployCheckJob`, `ConflictRequestReadJob`, `DeployConflictJob`.
- No one place said which cause each read ends; the cases E26, E33–E35, E40, E41, B26 and J7 in `docs/cases.md` were cells
  of that missing table.

## Decision

- `flow/ConflictEnding` is the table: per cause, the reads that end it in the order they run, and the stops each reads under.
- `RED_PIPELINE`: later green, closed request, staged fix. `MERGE`: closed request, staged fix. `REVERT`: staged fix.
- A later green reads under no stop or a deploy-read stop; the others under any stop but a pause.
- `DeployConflictJob` is the one reader: every 20 s it walks the table for each task in `DEPLOY_CONFLICT`.
- A read that may have changed the task hands the next read the task as it now is; one that ended the conflict ends the walk.
- A read of the host runs at most once a minute per task, as before; the worktree read every pass.
- Each read is a `job/ConflictRead`; a new ending is one row and one read, touching nothing else.
- A `revert` pressed on a conflict is a verb, not a read: it stays with `Move.actions()`.

## Rejected

- A row per cause in `TaskChart`: the chart holds moves between states, these are reads that trigger them.
- Every read every 20 s: three times the host reads for no case asking it.

## Reopen when

- An ending is found that the table cannot express without a guard outside it.
