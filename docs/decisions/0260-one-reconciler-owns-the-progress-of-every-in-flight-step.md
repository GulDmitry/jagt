# 0260 — One Reconciler owns the progress of every in-flight step

[← decisions](README.md) · on [0108](0108-a-stop-ends-on-the-read-that-disproves-it.md),
[0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md), [0144](0144-what-was-told-once-is-one-ledger-written-by-the-reducer.md) ·
spec: [`task-flow`](../../openspec/specs/task-flow/spec.md), [`task-stops`](../../openspec/specs/task-stops/spec.md)

## Status

Accepted 2026-11-26.

## Context

- 166 defect fixes since 2026-10-15, about half lifecycle, each patched alone in one of some twenty deciding jobs.

## Decision

- **One component decides progress**: `flow/Reconciler.decide(task, now)` over the table `flow/StepPolicy`, run by `job/ReconcileJob`.
  Each tick returns STAY, LAND, RETRY, RESTART or STOP per task; "supervisor" is `port/host/Supervisor`'s (0161).
  Every other writer (sessions, sweep, watchdog, Master answers, deploy reads) records a fact or a beat.
- **Input is stored facts alone**: the chart configuration, its regions' stamps, one `Beat(step, since, lastAt, attempts)` per step.
  A beat is written through the reducer, so a restart changes no decision. A step is derived from facts, never stored:
  sealed `flow/Step`, one `StepPolicy` row each.
- **The tick is door two** (`FlowDoorsFitness`): LAND is `FlowReports.tick`, eventless guarded edges over the CHECKS, APPROVAL,
  REQUEST, DEPLOY regions. A press stays door one; a reported status is a claim.

| step (in flight) | beat missed: retry · restart · named stop |
|------------------|-------------------------------------------|
| SESSION_TURN (turn open, session holds it) | nudge once · relay to a woken or fresh session once per generation · SILENT |
| VERIFICATION (status VERIFYING) | rerun a lost run · none · hand-back to the human naming the cause |
| HOST_READ (request open) | reread · none · WAIT past `checksMinutes`; red keeps the loop guards |
| INFRA_RERUN (`InfraPhase` armed) | `InfraRetries` budget · none · INFRA |
| JUDGE_ROUND (a claim open) | `undoClaim`, claim once · none · turn to the human |
| DEPLOY (an unfinished `DeployStep`, or a landed tip unread green or checker reading: the session may not leave) | finish from recorded steps; red once · none · DEPLOY_CONFLICT or DEPLOY_UNREAD |

- **A lost reach of jagt's own** suspends the row's clock and marks the board's head, never a card. An exhausted policy logs one ERROR,
  sets one `ToldOnce` mark, ends on the read that disproves it (0108). Loop guards become `StepPolicy` counters;
  `TaskStops` stays the one stop writer, called by `ReconcileJob` alone.
- **Faults**: a session lies, is silent or dies; checks read red, green, flaky, slow or unread; jagt restarts with an effect unrecorded;
  a token is stale. A tick follows every move. **Checked exhaustively on the real chart and `Reconciler.decide` in the commit gate**:
  S1 every status change is one fired edge; S2 no jagt move where the human holds the turn; S3 no `deploy` unless facts are green,
  resolved and approved; S4 no effect twice for one fact, a restart included; S5 a stopped task takes only the read its stop names.
  L: within B ticks of `StepPolicy`'s budgets and with no human move, every reachable state reaches deployed or closed, a human's
  advancing action, a stop naming its end, or the head naming a lost reach; JGraphT (test scope) finds the cycles.
- **A fixed defect becomes a case**, RED with the fix reverted: a fault move in the model or a named schedule line in the simulation;
  the `Case:` trailer names it.
- **Migration is one step kind at a time behind `ProgressOwnerFitness`**: no class but `ReconcileJob` decides; the count ratchets to 0.

## Reopens when

- The check passes 120 s (project away unread regions, never sample), no row fits a step kind, or a defect passes model and simulation.
