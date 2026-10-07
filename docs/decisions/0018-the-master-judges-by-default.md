# 0018 — The Master judges by default

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-07.

## Context

Run daily in `act`, the Master's verdicts closed most tasks to the human's satisfaction. `judge` only reads and
presses nothing, so it adds a reader without taking a step from the human.

## Decision

- An unset `master.mode` is `judge`; `off` and `act` are written out.
- With no brief named or copied, it judges by the shipped `master-brief.md.dist` (`MasterBriefs.file`), so getting
  started stays one copied file.
- `act` stays opt-in.

## Rejected

- `act` by default: it writes the shared branch, which no install should do before its human chose it.

## Reopen when

- A new install's first rounds come back with verdicts the human overrules more often than not.
