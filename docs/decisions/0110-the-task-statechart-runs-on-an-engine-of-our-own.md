# 0110 — The task statechart runs on an engine of our own

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-30. Extends [0006](0006-the-machine-owns-every-move-a-model-only-judges.md)
and [0108](0108-a-stop-ends-on-the-read-that-disproves-it.md).

## Context

- A task is a status plus conditions beside it, written from 40 `updateTask` sites in 24 files, and nothing says which
  condition may stand at which status or what a move does to it.
- That gap cost ten findings in one inventory (2026-10-29): an `INFRA` stop held a resolved deploy conflict two hours, and a
  stop on a merged request refused its follow-up.
- No JVM library checked 2026-10-29 has all four: a release within 18 months, maintenance, parallel regions, and a
  pure Spring-free step that returns effects as data.
- The closest, KStateMachine 0.38.1 (2026-06-17), is Kotlin-first and pre-1.0. It steps by suspending, fires its
  effects as callbacks, and does not document persisting parallel regions.

## Decision

- The status lifecycle is one region and each condition a parallel region of one statechart.
- A generic engine runs it, in a Gradle module of its own that names no jagt type and imports nothing but the JDK.
- The engine's step is pure: `(Configuration, Event) → (Configuration, Effects)`. Effects are data that the caller runs.
- A configuration is a plain record that jagt persists in `state.json`. The engine keeps no state.
- jagt's chart is data: one typed Java DSL file in `flow/` declaring regions, states, events, guards, exits and effects.
- A rule change is one row there and touches nothing else.
- A derived region (host, checks, infra) is declared as derived. It is computed from the last read and never stored.
- One reducer in `flow/` is the only writer of status and conditions. The `updateTask` sites become events fed to it,
  one condition per commit.
- A test validates the chart: every stored non-final state has an exit, a guard reads only other regions, and every
  state is reachable.
- A jqwik property drives random event sequences against the same invariants.
- The diagram is rendered from the chart into `docs/`, and a golden test pins it.
- Semantics follow SCXML, with KStateMachine as the reference: document order, exits before entries, and a
  run-to-completion step.

## Rejected

- KStateMachine behind a port: the adapter would rebuild the pure step it lacks, and the Kotlin runtime would land in `core/`.
- Spring Statemachine is archived and needs Spring. squirrel, stateless4j and easy-states are dormant, and Pekko FSM is flat.
- Commons SCXML has had no release since 2013, and its guards are script strings, not typed code.
- A YAML chart: guards are code, and a string guard compiles nothing.
- Keeping the per-caller writes with more tests: the ten findings were cells no test was written for.

## Reopen when

- A maintained JVM library ships parallel regions with a pure, data-returning step.
- The engine module grows past what the chart uses: history, deferred events or timers nobody asked for.
