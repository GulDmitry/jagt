# 0062 — A permission is the human's to grant

[← decisions](README.md) · rules: [`master`](../../openspec/specs/master/spec.md),
[`protocol`](../../openspec/specs/protocol/spec.md), [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-22.

## Context

A session's edits in the deploy worktree were denied by its CLI; it asked, the Master in `act` answered, and nothing
was granted. Later reports at `DEPLOY_CONFLICT` then replaced the card's conflict line with unrelated sentences.

## Decision

- A session stopped on a denied call reports `outcome=permission`; jagt writes `awaiting permission` into its line,
  and `MasterAnswerJob` never answers it. The signal is a typed field, not a model reading the question.
- At a status a human holds (`FlowRules.holdsItsLine`) a report leaves the line; a question alone replaces it, and
  the next report clears it back to the status's own.

## Rejected

- The CLI's own denial hook: a vendor event jagt would have to tie to a later question; the session already knows.
- The report's line in the hover: no surface carries it today, the log does.

## Reopen when

- A session asks for a right without `outcome=permission` and the Master answers it again.
