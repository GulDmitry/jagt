## Why

A sweep that read a green request replaced a standing question with the read's own line, so the card stopped asking while the session still waited for an answer.

## What Changes

- A round read never clears a `QUESTION` or `PERMISSION`; only the session's next report or a human answer does.
- The read may still move the status; the report and its message survive the move.

## Capabilities

### Modified Capabilities

- `task-flow`: statuses a report cannot move.
