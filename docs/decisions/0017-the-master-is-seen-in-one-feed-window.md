# 0017 — The Master is seen in one feed window

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-07.

## Context

In `act` the Master answered a question by opening a task and later closed another. The human saw one window vanish
and another open, with nothing naming the Master; the why sat only in jagt's machine log.

## Decision

- One `master` window in jagt's tmux session, for every project, follows `jagt-master.log` (`MasterFeedWindow`).
- The feed is a logback appender on the Master's classes: every step it logs is a line, nothing written twice.
- It replaces nothing on the board: the machine log was the only trace.

## Rejected

- One live Master session in that window answering every task: its context grows across tasks, and the roles would
  no longer read a round each on their own.

## Reopen when

- The feed is never read, or a step the human needed is missing from it.
