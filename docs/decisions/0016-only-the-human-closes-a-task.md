# 0016 — Only the human closes a task

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-07.

## Context

In `act` a session's question was answered by opening a second task. The human kept questioning the first session;
its next round changed nothing, the Master read it `ready`, and jagt closed the task, killing the session mid-talk.

## Decision

- The Master never closes a task: a `ready` round holding nothing to ship waits for the human (`MasterShip.ship`).
- `done` is no `master.mine` step: there is nothing left to keep.

## Rejected

- Closing only where the human typed nothing since the last report: an empty round is a finding the human has not
  read yet, whoever asked for it.

## Reopen when

- Empty ready rounds pile up on the board unread.
