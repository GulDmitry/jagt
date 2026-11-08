# 0149 — Triage is a stop read, not a checker

[← decisions](README.md) · rule: [`task-stops`](../../openspec/specs/task-stops/spec.md) · beside
[0126](0126-a-role-is-a-folder.md)

## Status

Accepted 2026-11-08.

## Context

- A stopped task asks its human the same questions each time: who looped, which family of defect it is, whether a lift is
  safe. Answering them by hand is a read of the trail, the commits and the stop's evidence.
- A checker (0126) is deploy-shaped: once per deploy, a `role.yml` with `jobs` and `readSteps`, a verdict that moves a task.
  A stop read has none of those: it moves nothing and is asked once per stop.
- `MasterStopJob` already reads a stop through `ChargedReviews` and the `RoundRead` schema.

## Decision

- `roles/triage/` holds `brief.md.dist` and `eval/*.yml` and **no `role.yml`**: `InstalledRoles` installs a folder with
  one as a checker, so with none it is never one.
- The answer is a `RoundRead` through `ChargedReviews`, mapped by `StopTriagePanel` into `StopTriage`: who looped
  (`session|master|case|jagt|none`), the defect family `A`–`M` or `unknown`, lift `safe|unsafe`, its evidence.
- Its spend is booked as the Master review's; a call kind of its own opens when the two must be told apart.
- The result is a component of the stop and ends with it: a lift clears it, the next stop is read afresh.
- It writes the stop's hover alone: presses nothing, notifies nobody, writes no git or code host.
- `roles.triage.enabled` is false by default; unset, nothing is read.
- `roleEval -Prole=triage` runs its own harness over `roles/triage/eval/*.yml`; its bar is in `docs/rules/testing.md`.

## Rejected

- Generalising the checker to a role that moves nothing: its `jobs`, `readSteps` and verdict would each gain a "none".
- An own port and schema: `RoundReviewer` and `RoundRead` already carry a verdict, one finding and a failure.

## Reopens when

- Triage's spend needs its own line in the usage report: a call kind of its own.
- A second stop read wants an answer shape other than one finding.
