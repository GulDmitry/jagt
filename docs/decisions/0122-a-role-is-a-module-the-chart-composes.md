# 0122 — A role is a module the chart composes

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md) · generalises
[0116](0116-qa-is-a-turn-holder-of-its-own.md), [0010](0010-the-humans-own-word-stands-over-the-master.md)

## Status

Proposed 2026-10-31 by the owner's direction; building from 2026-10-31: QA first, then the Master, then one eval.

## Context

- Adding QA touched 54 main files in 12 packages (+1575 lines, `7ce4c060^..1b3966c6`): holder enum, region, record,
  jobs, port, config keys, brief, cases and eval each name the role.
- The owner wants many roles, each switched on or off, and people adding their own: prompt, states and cases.

## Decision

- A task's chart is the core regions plus one region per installed role (shipped, or a `roles/<id>/role.yml` read at
  boot), set once at startup as the installed composition; with none set, the built-in roles. `enabled` stays live and
  gates the trigger and the job: a role off holds a region at rest, runs nothing and calls no model.
- A role is the **arbiter** (one, the Master, in code: rights, `act`, the feed window) or a **checker** (many, read-only,
  a typed verdict). A checker's region is one closed vocabulary, `CheckerPhase`; its moves carry the role id.
- A checker is declarative: `role.yml` (trigger from a closed predicate vocabulary, tool scope, model, outcomes),
  `brief.md`, `cases.md` (its step gate) and `eval/*.yml`. Outcomes are fixed core events: a round to the session,
  a question, a wait on a probe, a dispute to the arbiter else the human, a stop, a label. QA is the first.
- A role failing its lint (reachable, every state naming its exit) is skipped with its reason; the rest start.
- Config `orchestrator.roles.<id>.*`; the old `qa:` and `master:` keys are still read. A user's old brief copy wins.
- Turn order stays as built: a checker reading holds the turn; a dispute goes checker → arbiter → human.
- A module is a declared package set per ring; `roleEval -Prole=<id>` is the one harness; pinned by a rule test that no
  class outside a module names it, persisted names (`state.json`, `usage-by-day.jsonl`) allow-listed.
- Not yet: a user checker's round needs a generic session-brief step; until then only QA rounds.

## Rejected

- A checker whose prompt picks its next state: routing by prose, which 0072 removed. The model answers a verdict only.
- A user role writing code or acting: only the arbiter acts, bounded by `Offers`.
- One generic agent with prompt-only roles: no region, so no turn, no stop and no exit a read can disprove.

## Reopen when

- A real role needs an outcome the fixed core events cannot express.
- A checker's region needs a state the lint cannot prove exits.
