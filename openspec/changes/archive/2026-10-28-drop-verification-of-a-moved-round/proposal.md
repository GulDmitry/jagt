## Why

A 2.5-minute probe run outlived the human's `ship`; its failed result was relayed at CI_POLLING, the case door refused it and jagt stopped the task over its own stale result.

## What Changes

- `VerifyJob` applies a result only while the task stands at the status, since the moment, it was taken at; else it drops it and logs `verification outdated`.

## Capabilities

### Modified Capabilities

- `unattended-work`: the loop-stop requirement gains a scenario.
