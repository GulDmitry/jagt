## Why

A SHIPS or STALLED stop outlived a human's deploy, so the deploy check and the Master's repo job skipped the landed change.

## What Changes

- A deploy a human presses lifts the stop standing on the task once it is `DEPLOYED`.
- A deploy any other origin presses lifts nothing; the Master's and jagt's remain refused past a stop.

## Capabilities

### Modified Capabilities

- `unattended-work`: a human's deploy clears a stop; the press-a-verb scenario says so.
