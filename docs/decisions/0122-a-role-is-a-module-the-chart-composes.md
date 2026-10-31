# 0122 — A role is a module the chart composes

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md) · generalises
[0116](0116-qa-is-a-turn-holder-of-its-own.md), [0010](0010-the-humans-own-word-stands-over-the-master.md)

## Status

Proposed 2026-10-31 by the owner's direction. Built after the statechart follow-ups: QA first, then the Master, then one eval.

## Context

- Adding QA touched 54 main files in 12 packages (+1575 lines, `7ce4c060^..1b3966c6`): holder enum, region, record,
  jobs, port, config keys, brief, cases and eval each name the role.
- The owner wants many roles, each switched on or off, and people adding their own: prompt, states and cases.

## Decision

- A task's chart is the core regions (status, session, git, request, checks, approval, deploy, infra) plus one region
  per enabled role, composed at startup. A role off is never composed: no state, no job, no call.
- A role is an **arbiter** (one, the Master: may act where the human stands, bounded by `Offers`) or a **checker**
  (many: reads only, answers a typed verdict).
- A role module holds: id, kind, trigger (a guard over core regions), states of its own region, answer schema, outcome
  map (verdict → core events among the legal `Offers` alone), tool scope, model, brief, cases.
- A checker is declarative: `roles/<id>/role.yml` + `brief.md` + `cases.md`. Its trigger names predicates from a closed
  vocabulary; its outcomes are the fixed core events (a round to the session, a question to the human, a label).
  jagt compiles it into a region and lints it at startup: reachable, every state naming its exit; a role failing is
  refused with its reason and the rest start.
- Shipped checkers use the same format, QA first: if QA cannot be written as a `role.yml`, the format is wrong.
- The arbiter stays code: rights, `act`, the feed window.
- Config: `orchestrator.roles.<id>.{enabled, model, mode}` for every role.
- Turn holder: `SESSION`, `HUMAN` or a role id from the composed set. Precedence: human > arbiter > checker.
- One eval harness, `roleEval -Prole=<id>`, reads any role's cases.
- Pinned by: no class outside a role's module names it; the chart lint over all on, all off and each alone.

## Rejected

- A checker whose prompt picks its next state: routing by prose, which 0072 removed. The model answers a verdict only.
- A user role writing code or acting: only the arbiter acts, bounded by `Offers`.
- One generic agent with prompt-only roles: no region, so no turn, no stop and no exit a read can disprove.

## Reopen when

- A real role needs an outcome the fixed core events cannot express.
- A checker's region needs a state the lint cannot prove exits.
