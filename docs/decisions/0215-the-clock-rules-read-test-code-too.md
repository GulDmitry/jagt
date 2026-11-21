# 0215 — The clock rules read test code too

[← decisions](README.md) · rule: [`docs/rules/style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-11-21. Amends [0055](0055-jagt-speaks-english-and-keeps-utc.md).

## Context

- 0055's clock rules scanned `main` and `evalSupport` only; a test set reading the machine's clock passed unseen,
  against AGENTS.md "A check holds for all code".

## Decision

- The clock rules in `fitness.yml` scan `main`, `evalSupport`, `boardTest`, `e2e` and `linuxTest`.
- Named there with this why: the `boardTest` classes, `E2eWorkspace`, `MasterModeFlowTest` and its nested `Stalled` seed
  timestamps for a real server and a real browser, which a frozen clock cannot answer.
- The unit `test` sets are a departure: they read the real clock in about 320 places. It ends when each reads a fixed
  clock and the rows name those sets too.

## Rejected

- Naming every unit test reading the clock: a fixed clock answers each of them.

## Reopen when

- A unit test needs real time that no fixed or stepped clock can give.
