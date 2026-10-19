## Why

Nothing bounded how many starts jagt made unasked in a day: a loop of intake, auto-review polls and `act` moves
could start work for hours with nobody watching.

## What Changes

- Unattended starts are counted per local day, in memory; at `maxStartsPerDay` (default 50) a dated stop file stands.
- While it stands the Master only judges, and intake and auto-review start nothing; a human press is never refused.
- It lifts on the next local day, or when you press the header's `act stopped`, whose hover names the cause.

## Capabilities

### Modified Capabilities

- `unattended-work`: a daily ceiling on unattended starts.
