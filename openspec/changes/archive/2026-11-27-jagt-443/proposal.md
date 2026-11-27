## Why

A round's red checks read taken before a push landed after it, relayed and moved the task to `CI_FAILED`.

## What Changes

- A round read the head moved past is dropped whole: no relay, no read mark, no stop.

## Capabilities

### Modified Capabilities

- `review`: a dropped round read relays, marks and moves nothing.
