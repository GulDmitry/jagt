# 0134 — A stalled deploy conflict asks the Master first

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md)
· amends [0063](0063-a-stalled-deploy-conflict-is-the-humans.md)

## Status

Accepted 2026-11-04.

## Context

- A conflict handed to its session and left unchanged for `conflictStallMinutes` told the human at once, in `act` too.
- Where the Master acts it answers every question a session stops on; the stall was the one stop it never saw.

## Decision

- Where the Master answers (`Turns.next` gives it a `QUESTION`), the first stall is put as the session's question at `DEPLOY_CONFLICT`,
  its message the stall's reason (`TaskReducer.conflictReferred`).
- `MasterAnswerJob` answers it on its existing path and relays the decision to the same session;
  withheld, runaway and unusable still end with the human.
- The `Conflict` records that the Master was asked, and the stall clock restarts then; the mark ends with the conflict.
- A second stall of that conflict tells the human once, as 0063 does.
- Where the Master does not answer (`judge`, a right kept, a stop), or a question already stands, the first stall tells the human as before.

## Rejected

- A second mechanism for the Master's answer: a conflict is a question like any other, and the answer path already relays at `DEPLOY_CONFLICT`.
- Asking the Master on every stall: a Master that answered once and changed nothing is the human's next.

## Reopen when

- A Master answer to a stalled conflict is seen to change nothing, in more than a few cases: then the first stall may go to the human again.
