# 0245 — The first plan and a later plan are two rights

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-11-25. Amends [0214](0214-the-master-acts-by-default.md)'s default `mine`; [0064](0064-a-plan-the-human-asked-for-is-theirs.md) stands.

## Decision

- `MasterRight` gains `REPLAN`: approving a plan after the task's first approved one. `PLAN` is the first.
- An unset `roles.master.mine` is `[plan, replan, deploy, revert]`: both plan approvals stay the human's until `mine` is written.
- A later plan is one reaching `PLAN_PENDING` once a plan of the task was approved: `Holds.planApproved`, set by `TaskReducer.planApproved`,
  false in an old `state.json`. The status history is capped, so it cannot say it.
- A plan opened in `plan` mode (`PlanGate.REQUIRED`) stays the human's whatever `mine` holds.
- The approve-plan button's hover names which plan waits; no mark, no colour.

## Rejected

- One right with a count of plans: `mine` names steps, not thresholds.
- Reading the first plan from `RoundTrail` or the history: both are capped or about rounds, not plans.

## Reopen when

- A third kind of plan needs its own hand-over, or the Master's `replan` approvals are overruled more than its `plan` ones were.
