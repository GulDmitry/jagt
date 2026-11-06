## Why

Only a Claude session's log was read, so a Codex session's spend was never booked.

## What Changes

- A Codex rollout's turns are booked as unpriced calls.
- An agent CLI that keeps no readable log books nothing and still starts.

## Capabilities

### Modified Capabilities

- `unpriced-cost`: a Codex turn is an unpriced call.
