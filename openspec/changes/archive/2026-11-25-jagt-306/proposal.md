## Why

Probes run one by one with no shell, so a probe that execs into a compose service found no stack up and a task stopped twice on the same failure.

## What Changes

- The plan check refuses a `compose exec` probe with no `compose up` probe of the same repository listed before it.
- The session brief says a probe starts what it needs as an earlier probe.
- The `UNCHANGED_FAILURE` stop's next step names a probe needing a stack it does not start.

## Capabilities

### New Capabilities

### Modified Capabilities

- `acceptance-probes`: a compose exec probe follows its stack's start.
