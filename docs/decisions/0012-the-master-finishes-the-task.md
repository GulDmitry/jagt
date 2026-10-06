# 0012 — The Master finishes the task

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-06.

## Context

A request's quality gate failed on duplication its renames touched in older code. The session asked whether to
refactor; the Master, reading as a cautious judge, ruled it out of scope, then answered "override in Sonar" — a
step only a human can take — and repeated it, citing its own earlier answers as settled. The loop stopped on the
reader that should have been its strongest.

## Decision

- Every Master read opens on its goal (`MasterPanel.GOAL`): the task ready to merge, checks green, the ticket met,
  architecture and naming kept; where the session stalls or a check stays red, it finds the cause, decides the
  change and insists on it.
- A check the task's own request turns red is the task's to turn green, older code included; an override or a
  human's action is no answer while a change in the worktrees can pass it.
- Three answers over an unchanged tree change the approach — a fresh session, the best option taken — and only a
  ninth, a runaway, reaches the human; a decision the task did not move after is no settlement.

## Rejected

- Handing a repeated question to the human: in `act` a question reaching them is a bug unless `master.mine` keeps it.

## Reopen when

- A Master insisting on a change produces rounds the human reverts: the goal outweighs a rule it should not.
