# 0079 — A case's `by:` restricts who sets it off

[← decisions](README.md) · rule: [`cases`](../../openspec/specs/cases/spec.md) · follows:
[0073](0073-cases-are-the-humans-and-the-stand-comes-first.md), [0076](0076-the-turn-is-a-fact-on-the-task.md)

## Status

Accepted 2026-10-24, by the owner.

## Context

- `by:` was parsed, checked at startup and written into a miss, but no relay compared it: a case read as a filter it
  was not.
- `/mcp` tags every call `MCP`, and `MCP` counted as the Master, so a session's own call read as the Master's in a miss.
- The board's owner came from the status alone while the jobs asked `Turns.next`: two answers to whose move it is.

## Decision

- A step is sent only where a case names it at the status for whoever set it off; any other is a miss.
- "The Master" is `CaseBook.By.of(origin) == MASTER`, read through `CaseDoor.byTheMaster`; an MCP call is jagt's.
- `Move`'s owner maps `Turns.next`, which takes the stop as an input; what the Master left, gave up on or pressed
  without landing hands the owner back to the human (`TaskTurns.leftToTheHuman`).
- Every write of the turn goes through `TaskTurns`; one digest, `Turn.digestOf`.

## Rejected

- `by: session` on step cases: a session sets off nothing jagt relays but through jagt's own answer to it.

## Reopen when

- A real flow sets off a step through MCP that only a human or the Master may.
