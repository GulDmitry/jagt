## Why

A step that never landed stopped the task as `UNDELIVERED` because the session's window was not running; the window came back and the stop stood until a human lifted it, though jagt can see the window run.

## What Changes

- `UNDELIVERED` ends when the session's window runs again, not on a lift.
- `SessionAliveJob` looks at the window of each such task every minute, through the terminal seam the flow already asks, and ends the stop through `TaskStops`.

## Capabilities

### Modified Capabilities

- `infra-wait`: a stop for a step that never landed ends when its session's window runs.
