# 0111 — A relay acts only where the task still stands

[← decisions](README.md) · rule: [`cases`](../../openspec/specs/cases/spec.md)

## Status

Accepted 2026-10-30. Extends [0090](0090-a-relay-is-recorded-before-it-is-sent.md)
and [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md).

## Context

- A step is decided on a read and relayed later. Ten fixes since 2026-08-01 were a relay landing on a task the human,
  the Master or a read had moved in between (family C).
- Only 5 of 15 relay sites carried a `Claim`, and the other 10 each checked something of their own, or nothing.
- A write counter would refuse almost every relay. Usage, polls and silence stamps write the task all the time, and a
  Master verdict takes minutes.

## Decision

- `Standing` is where a task stands: its lifecycle status, the way its stop ends, and its turn's id and start.
- It is derived from the task and never stored. Writes that move none of the four leave it equal.
- Every `StepRelay` entry takes the `Standing` its caller decided on, or the `Claim` carrying it.
- An entry refuses with `TaskMovedOn` before the door when the live standing differs.
- A site with no read of its own takes the live standing at the moment it decides.

## Rejected

- A stored revision bumped on every write: metering and polling would drop most verdicts.
- A revision bumped only by "relevant" writes: every new writer would have to choose, and one that chose wrong is the
  bug again.
- A retry per site: it hides the race instead of refusing it.

## Reopen when

- A relay is refused although nothing a party acted on moved: the standing holds a fact it should not.
- A defect of family C lands through a write that leaves the standing equal.
