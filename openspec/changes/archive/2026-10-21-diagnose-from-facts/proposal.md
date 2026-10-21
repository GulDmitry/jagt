## Why

A bug-fix test red for another reason than the report's passes review, a session reads code for a cause before it has
facts, and how often the human had to act on a task shows only in its history file.

## What Changes

- The brief's QA role asks a bug-fix test to be RED on the report's own signal; a round whose test is not is wrong.
- With `orchestrator.agent.diagnose.enabled` on, the brief asks a session to diagnose a bug from facts and a control
  before code. Off by default.
- `stats` gets a `BY YOU` column: the moves a human made on each task.
- `BY YOU` replaces counting the human's moves by reading the history by hand.

## Capabilities

### Modified Capabilities

- `master`: a bug test red for another reason is wrong.
- `session-brief`: Diagnose.
- `board`: `stats` counts the human's moves.
