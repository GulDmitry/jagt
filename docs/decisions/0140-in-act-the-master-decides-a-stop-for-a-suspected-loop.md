# 0140 — In act the Master decides a stop for a suspected loop

[← decisions](README.md) · rule: [`task-stops`](../../openspec/specs/task-stops/spec.md)

## Status

Accepted 2026-11-05 by the owner's order: no stops in `act`. Amends
[0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md): `lift` is no longer the human's alone;
[0104](0104-loops-are-bounded-per-task.md): a `PACE` stop is the Master's too.

## Decision

- In `act`, with `MasterRight.LIFT` not in `mine`, a stop of trigger STALLED, SHIPS, ROUNDS, PACE or UNCHANGED_FAILURE is the Master's turn.
- MISS, QA_FAILED and PAUSE stay the human's; a stop a read ends (UNDELIVERED, CHECKS, INFRA, WAIT, DEPLOY_UNREAD) stays on its read.
- `MasterStopJob` asks once per stop, the ask recorded on the task before it is made, so a restart never asks twice.
- The answer's first line decides: `lift:`, `return:` (a lift, then the session told the instruction as an answer) or `human:`.
- Anything else, no answer, or a task waiting on a plan (for `return:`) leaves the stop the human's; that ends the Master's hold.
- The bound is one Master lift per task since the human's last lift or deploy, a constant; a new request does not restore it.
- `CaseDoor` lets the Master's `lift` through only for a stop it was asked about within that bound.
- The board keeps its one chip; no mark is added.
- `judge`, the default, is unchanged.

## Rejected

- A bound in `jagt.yml`: one more number nobody has evidence for.
- A model judging whether the loop broke before the stop is set: 0061's guard holds when the model is the loop.
- Letting the Master lift without a bound: a loop would pass through its own judge.

## Reopen when

- A task the Master lifted stops again on the same trigger in the same round more often than not: the bound or the ask is too loose.
