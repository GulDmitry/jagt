# 0177 — A missing project program is read by its own job

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md)
Rule: AGENTS.md "jagt's own reach failing is the board's, never a card's"

## Status

Accepted 2026-11-16, unasked; raises `startTriggers` from 53 to 54.

## Context

- A project's `verifyCommand` or `setupCommand` program the image lacks fails every verify run late, per task.
- The mark must end on the read that finds the program, so the read repeats while jagt runs.
- Resolving a program needs `adapter/Executables`; no existing job lives in `adapter/` and reads project config.

## Decision

- `ProjectProgramsReach` is a `Job`, every minute, marking a missing program in the reach ledger.
- A program spelled with a `/` (a wrapper in the repository) is not checked.

## Rejected

- A startup check refusing the start: one project's missing tool would stop every other project.
- Folding the read into an existing job: none reads project config from `adapter/`.

## Reopens when

- A second per-project program read appears: one job reads both.
