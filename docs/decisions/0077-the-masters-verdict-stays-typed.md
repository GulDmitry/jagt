# 0077 — The Master's verdict stays typed

[← decisions](README.md) · spec: [`master`](../../openspec/specs/master/spec.md)
· step 4 of [0072](0072-the-conversation-turn-is-the-machines.md)

## Status

Accepted 2026-10-24.

## Context

The Master answers through a schema, yet jagt rendered that answer to `master-review.md` and parsed it back by lines.
A repeat was word Jaccard ≥ 0.5 on a finding's pattern; a `do` line was found by its prefix; a verdict answered any
hand-back older than its file; repeat and answer counts lived in memory and a restart reset them.

## Decision

- Every role's answer is stored as written in `.jagt/master-review.json`, under the worktree's own `.jagt/`, out of
  git and off the board; `master-review.md` is rendered from it for a human and never read back.
- A verdict carries the hand-back jagt asked about, the task's `statusSince` at the ask, and answers it by equality.
- A finding's identity is its file, anchor and kind, the kind a schema enum; it repeats when the three come back.
- Work the Master would open as a task is the answer's `newTask` field.
- Rounds and answers are kept in `.jagt/master-rounds.json`, the last 20 of each.

## Rejected

- A line range as the anchor: it moves with every fix above it.
- The verdict on the task in `state.json`: the turn is changing that record in parallel; a worktree file is the
  verdict's own.
- A digest of the diff as the round: a hand-back over an unchanged tree would replay the old verdict.

## Reopen when

- The typed turn of 0072 carries a round id: the verdict takes that in place of `statusSince`.
- A model names one finding with a new anchor or kind each round, so a stalled loop never counts as repeated.
