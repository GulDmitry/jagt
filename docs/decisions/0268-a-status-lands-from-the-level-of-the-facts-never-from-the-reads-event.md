# 0268 — A status lands from the level of the facts, never from the read's event

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md) ·
on [0260](0260-one-reconciler-owns-the-progress-of-every-in-flight-step.md), [0108](0108-a-stop-ends-on-the-read-that-disproves-it.md),
[0216](0216-the-statechart-engine-follows-harel-and-scxml.md)

## Status

Accepted 2026-11-28. Supersedes the choice of `1ce10cd91` (2026-10-29), held by no record: a round read moves only a task waiting on the host.

## Context

- `StatusSets.READ_FROM` names the statuses a read may move; it changed three times in four weeks: `1ce10cd91`, `7e0dce3d2`, then a third
  edge for a session that went silent after a CI retry.
- A read edge fires on the read's event: on 2026-11-26 a task stood IN_PROGRESS with green checks, its deploy never offered.
- `FlowRules.readLands` and `deployReadLands` decide per status and read, so each new path needs another edge.

## Decision

- **A delivery status follows the stored facts**: eventless guarded edges (`StatechartBuilder.always`, 0216) over the CHECKS, APPROVAL,
  REQUEST and DEPLOY regions land the status the facts name, whatever event was missed.
- **A read writes facts only**: the sweep and the deploy read record checks, approval, head and threads, and land nothing.
- **The landing is door two**: `FlowReports.tick`, called by `job/ReconcileJob` alone (0260); a press stays door one.
- **Facts outrank claims**: a reported status the facts contradict is held, as `FlowRules.reported` holds REVERTED.
- **The first eventless edge leaves IN_PROGRESS**: a silence that ended green lands where the facts say, a LAND that strikes no `MasterBrake`.
- **0108 reaches statuses**: a status an outside condition set ends on the read that disproves it.
- Go once the tick lands: `FlowReports.read`, `readDeploy`, `endRedConflict`, `FlowRules.readLands`, `deployReadLands`, `StatusSets.READ_FROM`.

## Rejected

- One more status in `READ_FROM` per defect: each fix reopened which status a read may move.
- A read edge from every status: a green read lands REVIEWED over a status its session works in, the defect `1ce10cd91` fixed.

## Reopen when

- A status must follow an event no stored fact records.
