# 0081 — A verdict answers a turn

[← decisions](README.md) · spec: [`cases`](../../openspec/specs/cases/spec.md) · amends:
[0077](0077-the-masters-verdict-stays-typed.md) (what a verdict answers)

## Status

Accepted 2026-10-24.

## Context

0077 keyed a verdict on the task's `statusSince`, to be replaced once the typed turn carried a round id. `Turn.id`
counted relays alone, so two hand-backs with no relay between shared one.

## Decision

- A report closing a turn starts the next holder's with a new id, as a relay does.
- A verdict stores the `Turn.id` it was asked about and answers that turn alone; a restamp moves it to the turn in front.

## Rejected

- `Turn.id` counting relays alone: a hand-back the human asked for by typing into the session would read as judged.
- Keeping `statusSince`: a clock reading the turn already replaces, and a status repeated keeps it.

## Reopen when

- A status move neither a relay nor a report makes needs a new verdict.
