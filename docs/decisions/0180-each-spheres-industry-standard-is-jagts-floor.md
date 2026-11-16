# 0180 — Each sphere's industry standard is jagt's floor

[← decisions](README.md) · on [0162](0162-a-check-holds-for-all-code.md) ·
map: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds)

## Status

Accepted 2026-11-16 by the owner's order: jagt is an enterprise application, and no sphere may lag its standard.

## Context

- The 2026-11-16 audit (JAGT-121) rated jagt against ISO/IEC 25010, OWASP ASVS 5 level 2, Spring Boot's production-ready
  features, the twelve-factor app, SLSA build level 2 with an SBOM, and the test pyramid.
- Of 86 rows, 13 were missing and 14 partial; line coverage was never measured (no JaCoCo).
- The refresh asked only inward questions: duplicates, contradictions, folds, defects. Checks were born from failures.

## Decision

- **Each sphere's industry standard is the floor**: architecture, code quality, testing, QA, security, operations,
  supply chain. A departure is a decision record.
- **A new kind names its sphere's standard and the check pinning it before its first code**, in the kinds table.
- **The floor holds for both stacks**, the Java backend and the board's JavaScript: the practices are shared.

## Rejected

- Rules born only from defects: an absent gate never fails, so its gap never shows.

## Reopen when

- Never by convenience; only when a standard itself changes.
