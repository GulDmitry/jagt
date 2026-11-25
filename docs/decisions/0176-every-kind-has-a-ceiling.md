# 0176 — Every kind has a ceiling

[← decisions](README.md) · on [0162](0162-a-check-holds-for-all-code.md)
Rule: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds)

## Status

Accepted 2026-11-16 by the owner's order.
Amended by [0209](0209-one-fitness-yml-declares-every-fitness-function-in-a-reserved-tree.md):
its numbers move from `complexity-ceilings.json` into `fitness.yml`.

## Context

- Each kind of thing (a CI job, a port, a role, a Gradle module, a dependency) grows by one at a time, each looking harmless.
- A cap written as prose is never read at the moment of the addition; a count in a test is.
- Cycle-freedom between packages is already a zero rule, `RingsFitness`; a count is a different shape.

## Decision

- `FitnessTest` holds one ratchet per counted kind, all numbers in `fitness.yml`.
- Each kind is a rule listing its items by name, so a failure names the new item.
- Above the stored number fails; below it fails too, asking to lower the number: the ratchet only tightens.
- Raising a number needs a decision record in the same commit.
- Counted: CI jobs and triggers, source sets, Gradle modules, build-logic files, declared dependency lines,
  start triggers, task statuses, capabilities, ports, startup checks, roles, spec capabilities, rule docs, public surface.
- The count ratchet is own code: no library counts CI jobs, triggers or kinds.
- Zero rules stay where they are; a scanner finding a violation of one fixes it, never freezes it.
- `reducer-zero-time` is a zero rule: `TaskReducer` passes no literal time; a timeless door uses `step(task, move)`,
  which refuses an effect that reads one.
- The refresh reviews each number against what the kind is for.

## Rejected

- One cap for the whole architecture: it names no kind, so no one knows what to remove.
- A ceiling only above: a deleted item would leave headroom for a new one.

## Reopen when

- A kind's count moves every week for a reason no record names: the scanner measures the wrong thing.
