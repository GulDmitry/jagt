# 0064 — A plan the human asked for is theirs

[← decisions](README.md) · rules: [`master`](../../openspec/specs/master/spec.md),
[`task-flow`](../../openspec/specs/task-flow/spec.md#requirement-plan-first-is-a-gate-jagt-holds)

## Status

Accepted 2026-10-22. Amends [0059](0059-plan-first-is-jagts-gate-not-the-clis-plan-mode.md).

## Context

A task opened in `plan` mode, so the human could read the plan, was approved by the Master in `act` (`mine: []`) and
shipped as a request holding only the session's review document. jagt also emptied its log at each start, so the
previous run's evidence was gone.

## Decision

- The Master's `go` opens only a gate autoplan closed; `PlanGate.REQUIRED`, already written for `plan` mode, is the fact.
- In `act` the Master still reads such a plan: findings go back as in `judge`, a ready one waits for `approve-plan`.
- `master.mine` unset is `[plan]`: approving any plan is the Master's only where the human removed it, one control.
- At start each log rolls into its one `.1` (`LogFileRoll`), so a restart keeps the run before and two files at most.

## Rejected

- A separate origin field on the task: the gate already tells `plan` mode from autoplan.
- Skipping the read of a `plan`-mode plan: the human loses the findings judge mode gives, for one read saved.
- Appending across restarts: the `master` window would open on the run before instead of this one.

## Reopen when

- A plan reaches `PLAN_PENDING` again after approval and the Master approves what the human meant to read.
