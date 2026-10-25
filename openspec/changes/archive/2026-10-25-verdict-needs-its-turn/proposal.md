## Why

The Master read a hand-back while the human pressed `ship`; its not-ready verdict then relayed `round-not-ready` and
set `IN_PROGRESS`, overwriting `SHIPPING` and leaving the session two contradictory steps.

## What Changes

- A verdict acts only while the task has the status and turn it was read at, checked under the state lock.
- Otherwise it is dropped and logged with the turn it answered and the turn now.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: a human's verb landing while the Master reads drops its verdict.
