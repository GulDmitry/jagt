## Why

`Jobs` ticked every second by hand; Spring's `TaskScheduler` with a `Trigger` is the library for it.

## What Changes

- Each job is scheduled with a dynamic `Trigger` reading `job.every()`, stamped from the start of the last run.
- `run <job>` is due at once, not at the next tick.

## Capabilities

### New Capabilities

### Modified Capabilities

- `unattended-work`: the one scheduler replaces the one ticker; run now is immediate.
