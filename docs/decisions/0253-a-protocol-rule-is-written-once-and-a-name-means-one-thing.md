# 0253 — A protocol rule is written once and a name means one thing

[← decisions](README.md) · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- An audit found 18 copies of `isBlank() ? null` in protocol records, three of them stripping and the rest not.
- The web-link rule stood in three records, the plan-section scan in three plan readers.
- A record kept the list a caller gave it (17 in `ProjectConfig`), so a later change by the caller reached the record.
- Two top-level records named `Standing` and a third nested in `TaskView` shadowed each other at every import.

## Decision

- **`task/text/Blank.toNull` is the one blank rule** of the tree: blank reads as absent, everything else is stripped;
  `Wire.absent` hands a message field to it, and no record carries its own.
- **`Wire.webLink` is the one web-link rule**; `PlanSection` is the one heading and fenced-block scan of a plan.
- **A record copies a list it is given** (`List.copyOf`).
- **A top-level class name is used once**: the session record is `SessionStanding`.
- A transcript path holding a NUL character, the one the file system refuses, is dropped by `SessionHookReport` as a violation.
- **Rows** `blank-normalisation-copies` (all main code, `Blank.java` apart, max 0), `web-link-rule-copies` (max 1 file),
  `uncopied-record-lists` (max 0) and `duplicate-simple-names` (archunit measure, one item per shared name,
  max 2: `Layout`, `Violation`, only falling).

## Rejected

- Stripping only where a record stripped before: a padded field would read differently by message.
- A fitness engine measure for the blank copy: a one-line pattern needs the scan, not a class graph.

## Reopen when

- A copy passes by another spelling (a helper with the same ternary): the rows read syntax, not intent.
