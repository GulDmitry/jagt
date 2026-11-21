# 0216 — The statechart engine follows Harel and SCXML

[← decisions](README.md) · rule: [`statechart`](../../openspec/specs/statechart/spec.md) · terms: [glossary](../rules/glossary.md#statecharts)

## Status

Accepted 2026-11-21, owner's order of 2026-11-20. Amends [0110](0110-the-task-statechart-runs-on-an-engine-of-our-own.md),
whose "Reopen when" it meets: history and the rest were asked for.

## Context

- 0110's engine named its parts in its own words (edge, condition, stored and derived region, chart step) and held
  parallel regions only; compound states were emulated by set-source groups.
- The owner asked for a real statechart: Harel's semantics and the academic vocabulary.

## Decision

- Every engine type carries its term from the glossary: `Statechart`, `Transition` (source, target, trigger, guard,
  actions), `Guard`, `Active` as SCXML's `In()`, `Datum` of the data model, `Macrostep`.
- A derived region is no region: it is a data-model value a guard reads, memoised once per macrostep.
- The engine implements compound and parallel states, initial pseudostates, entry and exit actions, shallow and deep
  history, `done.state` completion, eventless transitions, choice pseudostates and do-activities, by SCXML's
  algorithm; W3C IRP cases that need no `send`, `invoke` or scripting pin it.
- A choice decides after the transition's exits, as UML's dynamic choice; no junction is built.
- The step stays pure; the caller persists a configuration (region to leaf) and a history map beside it, so an old
  flat configuration reads as no history.
- `flow/` keeps its behaviour; set-source groups stay sugar for several transitions.
- **`spec-capabilities` is 52** ([0176](0176-every-kind-has-a-ceiling.md)): `statechart` is a generic engine's contract; `task-flow`,
  at its page budget, holds jagt's chart, not the engine.

## Rejected

- A sealed engine event for completion: a factory of the caller's event type keeps flow's trigger lambdas.
- Cross-region targets: no flow case needs one; IRP 403c's region-to-region conflict is not ported.

## Reopen when

- A flow case needs a transition into another region, or `send`, delay or `invoke`.
