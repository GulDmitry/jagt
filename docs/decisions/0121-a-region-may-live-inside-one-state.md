# 0121 — A region may live inside one state of another

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-31. Extends [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md).

## Context

- A QA round and a Master hand-over exist only while a task stands at one status, yet their marks live on the task at
  every status and are cleared by hand when the status moves.
- The engine has parallel regions only. Nothing says a region means something inside one state of another.

## Decision

- A region may be declared inside one state of another stored region.
- It takes no edge while its parent stands elsewhere.
- When the parent leaves that state, it runs the exit effects of the state it stood in, then goes back to its initial state.
- Validation checks its reachability and exits as for any region, and rejects a parent that is unknown or itself scoped.
- The diagram draws it inside the parent state it lives in.
- The engine stays JDK only and names no jagt type.

## Rejected

- Full compound states with a least-common-ancestor exit: the chart's fan-in edges overlap and cannot nest into one tree.
- Clearing the marks by hand at each status move: that is the gap [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md) closes.
- Scoping a region to a set of states: one state is all the QA and Master regions need.

## Reopen when

- A region must live inside a state of a scoped region, or inside two states at once.
- A parent's exit must run its children's entries, which this engine does not model.
