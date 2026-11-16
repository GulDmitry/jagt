# 0186 — The engineering capability is the one inventory of gates

[← decisions](README.md) · on [0180](0180-each-spheres-industry-standard-is-jagts-floor.md) · beside [0176](0176-every-kind-has-a-ceiling.md)
Rule: [`engineering`](../../openspec/specs/engineering/spec.md)

## Status

Accepted 2026-11-16 by the owner's order: jagt is a template a new project copies, the gates in one place.

## Context

- Audited 2026-11-16 against ISO/IEC 25010, OWASP ASVS 5, Spring Boot production-ready, twelve-factor, SLSA and the test pyramid:
  86 rows, gates scattered over rules, decisions and a task's notes.
- A project copying jagt had no one list of what is gated, by which check, in which stack.

## Decision

- `openspec/specs/engineering/spec.md` states every gate stack-agnostically; a scenario names its check per stack or its gap task.
- [`docs/practices.md`](../../docs/practices.md) is the entry page: rows by sphere, one column per stack, the unit to copy.
- `EngineeringSpecTest` fails on a scenario naming a test class that exists in no source set; `PracticesPageTest` on a rule it never links.
- `specCapabilities` 46 to 47: the one new capability.

## Rejected

- The audit matrix kept in a task's notes: it dies with the task and no test reads it.
- One requirement per audit row: 86 requirements, most restating a spec that owns the behaviour.

## Reopen when

- A gate's scenario names a check by prose and no test: the capability pins nothing it claims.
