## Why

The board answered whose move it was from the status alone, while the jobs asked `Turns.next`: a question the Master
answers in `act` read as the human's and pinged them. A case's `by:` was parsed and never checked, and a session's
own MCP call counted as the Master's.

## What Changes

- `Move`'s owner maps `Turns.next`, which also takes whether jagt stopped the task; a step the Master left, a question
  it gave up on or a deploy it pressed that did not land is the human's again.
- A step is sent only where a case names it at the status for whoever set it off; an MCP call sets off jagt's.
- Every write of the turn goes through `TaskTurns`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: a question the Master answers, and a deploy it makes, are its move, not yours.
- `cases`: `by:` restricts who sets a step off; the turn's holder takes the stop.
