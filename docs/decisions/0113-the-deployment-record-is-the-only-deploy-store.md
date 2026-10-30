# 0113 — The deployment record is the only deploy store

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md), [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-30. Completes [0112](0112-a-deploy-is-a-saga-record-advanced-by-one-runner.md)'s migration and
supersedes in part its "written beside `deployCommit` and `deployFixes`" and
[0109](0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md)'s `deployCommit`, `deployFixes` and `deployTip`.

## Context

- Commits 1 to 5 of the migration moved every reader and writer of a deploy's merge, fixes, push time and conflict onto `TaskState.deployment`.
- Nothing wrote the per-repository fields any more, yet the specs and decision 0109 still named them.

## Decision

- `Deployment` is the only place a merge, its fixes, the push time and a conflict are kept; `TaskRepo` holds none of them.
- A state file written before it is read once, by `LegacyDeployment`, into the record; a later write drops the old fields.
- A revert walks the record's targets backwards, each forgetting its merge once reverted (`RevertRunner`).
- `git` and `infra-wait` name the record, not the old fields.

## Kept

| unit | why it stays |
|------|--------------|
| `DeployConflicts`, `DeployConflictJob` | the job still reads each waiting conflict's worktree on disk, which no record holds |
| `DeployFixWorktrees` | `DeployPipelines` opens the fix worktree of a red pipeline through it |
| `DeployRedLine` | `LegacyDeployment` tells an old red hand-back by its line, and `DeployPipelines` writes it |
| `DeployTargets.stopped` | it pairs the conflicted target with its configuration and the status, which `IdeProject`, `DeployService` and `RevertService` need |
| `Deployment.Target.deployBranch` | the branch the press was made on, which `DeployServiceTest` pins |

## Rejected

- Deleting the kept units here: each has a reader, and folding them in changes jobs and tests, not words.

## Reopen when

- A reader of a kept unit is gone, or the deploy branch of a press is read from config alone.
