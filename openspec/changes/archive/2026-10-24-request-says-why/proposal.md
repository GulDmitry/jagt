## Why

A task's history said who asked for a step, never why, so a step nobody expected could not be read back (0074).

## What Changes

- `update_agent_status` and `initialize_task` require `why`: one sentence of at most 120 characters.
- The reason is kept on the step the request writes and read in that step's hover in the history.

## Capabilities

### New Capabilities

### Modified Capabilities

- `protocol`: a request that sets off a case says why.
- `board-workspace`: the history shows a step's reason in its hover.
