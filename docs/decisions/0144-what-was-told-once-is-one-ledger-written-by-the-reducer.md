# 0144 — What was told once is one ledger, written by the reducer

[← decisions](README.md) · extends [0076](0076-the-turn-is-a-fact-on-the-task.md)

## Status

Accepted 2026-11-07.

## Context

- Four task-scoped "already told once" facts lived in three homes: `AutoReviewTold` on the round, and the marks
  `alerted:silence` and `verify:failed` on the turn.
- Each writer reached its own home directly (`withTold`, `withMark`, `withoutMark`), so one clear could hit the wrong fact.

## Decision

- `task/ToldOnce` on the round holds one entry per `Kind`: the key the fact was told for. Absent reads as never told.
- `said(kind, key)` is true only for the key told; another key (a new round, a new silence, a new tree) tells again.
- `TaskReducer.told` and `TaskReducer.untold` are the only callers of `withTold` (`FlowDoorsTest`).
- A reset names the kinds it clears: the auto-review reset never clears the silence or the failed verification.
- `LegacyTold` moves the two old marks into the ledger on read; the stored `told` object reads, ignoring unknown ids.

## Rejected

- One field per fact on `Round`: a fifth fact adds a component, and the round is at its ceiling.
- A generic string map on the task: no kind to name in a reset, so any writer could clear any fact.

## Reopen when

- A told fact must outlive its task, or a fifth kind has a key that is not one string.
