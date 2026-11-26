# 0261 — A read carries its generation, and every fault meets every step

[← decisions](README.md) · on [0260](0260-one-reconciler-owns-the-progress-of-every-in-flight-step.md),
[0108](0108-a-stop-ends-on-the-read-that-disproves-it.md), [0144](0144-what-was-told-once-is-one-ledger-written-by-the-reducer.md) ·
spec: [`task-flow`](../../openspec/specs/task-flow/spec.md), [`protocol`](../../openspec/specs/protocol/spec.md)

## Status

Accepted 2026-11-26.

## Context

- A fact read before a move can land after it and overwrite the newer state (a host read, a checker read, a late report).
- 0260 names faults and steps but not every pair; a pair nobody wrote is decided by whichever code meets it first.
- The DEPLOY hold left two gaps: the tick before the acceptance check opens, and an acceptance wait on a down environment.

## Decision

- **A read carries the generation it was taken at**, the task's state revision when it started. One landing older than the state it
  would move is dropped, logged `read.stale`, and read again next tick. S6, checked with S1-S5 in the commit gate: no read moves a newer state.
- **Fault by step is total**: every pair is retry (r), restart (R), stop (S) or ignored (i), each with its why; the model check fails on an unlisted
  pair. The table adds no step: a step's own row stays in 0260.
- **DEPLOY is in flight until the post-merge pipeline and the acceptance check are green**: the tick between the green read and the acceptance check
  opening is held, the hold being a fact (merged, acceptance not yet green), never a flag. An acceptance wait on a down environment is `unread`: it
  consumes the DEPLOY budget and ends in DEPLOY_UNREAD, which releases the hold (L: a hold never outlasts B ticks).
- **The one open rule: an undeclared report field is ignored**, so a CLI one version ahead still works. Every other rule of `protocol` stays closed.

| fault \ step | TURN | VERIF | HOST | INFRA | JUDGE | DEPLOY | why |
|--------------|------|-------|------|-------|-------|--------|-----|
| session lies | i | i | i | i | i | i | a report is a claim; LAND needs the guarded fact |
| session silent | r→R→S | i | i | i | i | i | nudge, relay, SILENT; the other steps run without the session |
| session dies | R | i | i | i | i | i | a fresh session takes the turn; the others are jagt's own runs |
| checks red | i | S | S | r | i | r→S | loop guards decide; infra red spends `InfraRetries`; deploy red once, then DEPLOY_CONFLICT |
| checks green | i | i | i | i | i | i | green is the tick's LAND, never a fault |
| checks flaky | i | r | r | r | i | r | rerun or reread; the beat's attempts bound it |
| checks slow | i | r | S | r | i | S | a missed beat; HOST_READ waits past `checksMinutes`, DEPLOY ends DEPLOY_UNREAD |
| checks unread | i | i | r | i | i | S | jagt's reach: clock suspended, head marked; HOST_READ rereads, DEPLOY_UNREAD after its budget |
| restart, effect unrecorded | i | r | r | r | r | R | S4: the beat is stored, so a rerun or reread repeats no effect; DEPLOY finishes from recorded steps |
| token stale | i | i | i | i | i | i | a lost reach: the head names it, no card stops |
| read lands late | i | i | i | i | i | i | dropped by generation, no beat consumed |
| job throws every tick | i | i | i | i | i | i | jagt's own failure: one ERROR, the head marks it, the other jobs' ticks run on |

## Rejected

- A timestamp as the generation: clocks tie and skew; a revision counts moves.
- A closed schema for report fields: a CLI one version ahead would be refused for a field nothing reads.

## Reopens when

- A pair needs a fifth outcome, or a read dropped as stale was the only one that would have landed.
