# 0072 — The conversation turn is the machine's

[← decisions](README.md) · cases: [`docs/cases.md`](../cases.md)
· supersedes in part: [0065](0065-jagts-own-texts-and-loops-are-held-to-one-contract.md)

## Status

Accepted 2026-10-24, on a study of 1036 commits since 2026-08-01.

## Context

433 of them fix a defect, 311 in October. The session ↔ Master families (state overwritten, turn collision, model
overreach, unbounded loop, prose routing, and half of text-vs-text) recur every week under one root: `FlowRules`
owns status × verb, and nobody owns the conversation turn. Each job re-derives whose move it is from the message
text and maps held in memory; each fix added one more key, regex or filter.

## Decision

- A defect is counted: its `docs/cases.md` row and a `Defect-family:` trailer on the fix commit.
- What a report means is a typed field on the task, never a marker parsed back out of its line.
- The turn becomes a fact on the task: who holds it, what it waits for, which replies it takes; one function in
  `flow/` decides it, every relay opens one, every report closes one, an unexpected reply is refused at the door.
- The Master's verdict stays typed end to end; its jobs dispatch on the turn, not on their own filters.
- A test stand with real models runs under `refresh` only, after the typed turn; QA joins as a turn holder last.
- Stop: fixing a turn defect with an in-memory key, a regex, a sentinel string, a brief edit or a new job filter.

## Rejected

- A hand-written case per allowed path: the turn table enumerates; cases are examples proving its rows.
- More Master context as prose: a typed input contract per read kind instead, measured before kept.
- A big-bang protocol rewrite: one step at a time, each with its spec change.

## Reopen when

- The trailer counts show the turn families no lower after the typed turn ships.
