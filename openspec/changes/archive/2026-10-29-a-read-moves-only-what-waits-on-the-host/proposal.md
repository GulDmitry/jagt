## Why

A green read landed `REVIEWED` or `APPROVED` over a task whose session was at work (follow-up work on an open request, a hand-back, a plan), overwriting the status the session holds.

## What Changes

- A round read moves only a task waiting on the host (`FlowRules.waitsOnTheHost`: `CI_POLLING`, `REVIEWED`, `APPROVED`); elsewhere it records the checks and approval and nothing else.

## Capabilities

### Modified Capabilities

- `task-flow`: statuses a report cannot move.
