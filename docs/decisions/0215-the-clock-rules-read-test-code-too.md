# 0215 — The clock rules read test code too

[← decisions](README.md) · rule: [`docs/rules/style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-11-21. Amends [0055](0055-jagt-speaks-english-and-keeps-utc.md).

## Context

- 0055's clock rules scanned `main` and `evalSupport` only; a test set reading the machine's clock passed unseen,
  against AGENTS.md "A check holds for all code".

## Decision

- The clock rules in `fitness.yml` scan every source set, sharing one set and one except list through a YAML anchor.
- Named there with this why: the `boardTest` classes, `E2eWorkspace`, `MasterModeFlowTest` and its nested `Stalled` seed
  timestamps for a real server and a real browser, which a frozen clock cannot answer.
- Every unit test reads a fixed clock. Left in the except list as pending: `ConfigServiceTest` and
  `PasswordLoginOutcomeTest`, which end when their owners' work lands.

## Rejected

- Naming every unit test reading the clock: a fixed clock answers each of them.

## Reopen when

- A unit test needs real time that no fixed or stepped clock can give.
