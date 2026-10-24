## Why

A live install read requests at `reviewed` and `deployed` every 12–45 minutes for 18 hours: 71 sweeps, 109 headless
reads, nothing new. Only a reviewer moves such a request on, and nothing jagt reads without a model says one wrote.

## What Changes

- A request at `REVIEWED`, `APPROVED` or `DEPLOYED` is read every `maxIntervalMinutes`, never on the ramp.

## Capabilities

### New Capabilities

### Modified Capabilities

- `unattended-work`: the pace of a request only a reviewer moves on.
