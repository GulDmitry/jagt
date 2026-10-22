## Why

A plan probe killed by jagt's own restart came back as exit 137, so the session was told its probe failed and to fix it.

## What Changes

- A command jagt kills because it is stopping raises `Processes.Stopped` instead of returning an exit code.
- A stopped probe therefore gets no verdict and nothing is relayed.

## Capabilities

### New Capabilities

### Modified Capabilities

- `runtime`: a command killed as jagt stops tells its caller it was stopped.
