# 0155 — The qa role is called acceptance

[← decisions](README.md) · rule: [`acceptance`](../../openspec/specs/acceptance/spec.md) · renames the role of
[0116](0116-qa-is-a-turn-holder-of-its-own.md) and [0124](0124-the-qa-brief-keeps-the-rules-that-measured.md)

## Status

Accepted 2026-11-09 by the owner's order: a full rename, no alias.

## Context

- The role checks each acceptance criterion of a ticket on a deployed environment; "qa" reads as local testing in a session.
- 0116, 0122, 0124 and 0126 keep saying `qa`: they record what was decided then.

## Decision

- The role id, folder, config key, classes, enums, steps, files, eval source set, Gradle tasks and the spec capability are `acceptance`.
- The board's pass label is `accepted`; no failing label existed, so none is added.
- `orchestrator.roles.qa` and the top-level `orchestrator.qa` are refused at start by `ConfigCheck`, naming `roles.acceptance`.
- A human's `roles/qa/*` files, session brief section `## qa-round` and `by: qa` cases are no longer read; they move by hand.

## What `qa` survives as

| where | what | why |
|-------|------|-----|
| `core/task/LegacyQa`, `LegacyRoles`, `StoredTask.Unmigrated` | top-level `qa`, `roles["qa"]`, marks `qa`, `qa:digest`, `qa:rows`, step `qa-round` | a state file written by an older version still loads, and is written back renamed |
| `ActionOrigin.ACCEPTANCE`, `TaskStop.Trigger.ACCEPTANCE_FAILED` | `@JsonAlias` `QA`, `QA_FAILED` | the same state files hold them |
| `Step.find` | id `qa-round` | a turn left open across the upgrade |
| `OrchestratorPaths.legacyQaVerdicts` | file `qa-verdicts` | read where `acceptance-verdicts` is absent |
| `ConfigCheck` | keys `qa` and `roles.qa` | the refusal's own words |
| `roles/master/brief.md.dist`, `roles/session/brief.md.dist` | the panel row `QA` | a code-review role that asks for tests, not this role; unchanged |
| `docs/decisions` 0116–0126, `openspec/changes/archive` | prose | history |

## Reopens when

- A legacy reader is dropped once no state file older than this decision can exist.
