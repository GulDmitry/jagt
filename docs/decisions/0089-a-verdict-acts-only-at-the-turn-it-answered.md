# 0089 — A verdict acts only at the turn it answered

[← decisions](README.md) · spec: [`cases`](../../openspec/specs/cases/spec.md) · extends:
[0081](0081-a-verdict-answers-a-turn.md) (what a verdict answers, now checked at send time)

## Status

Accepted 2026-10-25.

## Context

- A Master verdict read at REVIEW_PENDING landed after the human pressed ship.
- It overwrote SHIPPING with IN_PROGRESS.
- Seen on the live stand: runs deploy-conflict/judge and wrong-review-thread/judge.

## Decision

- Every Master verdict (ready, not-ready, question, plan-go) and every answer claims under the state lock.
- The claim holds only when the live status and turn match the ones the verdict answered.
- The send happens outside the lock.
- A failed send undoes the claim and stops the task UNDELIVERED.
- A dropped verdict counts no round.

## Rejected

- Holding the lock across the send: tmux typing takes seconds and would block every task.

## Reopen when

- A verdict must act on a task that has moved since it was read.
