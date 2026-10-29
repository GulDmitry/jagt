## Why

A task was stopped as a loop on three not-ready plans in 24h although each answered a reworked plan, and the one
daily count of unattended starts is spent by every task alike.

## What Changes

- A round of one step counts toward `loopBreaker.rounds` only while it answers the session report the previous round answered.
- `loopBreaker.perHour` (default 20) bounds one task's unattended moves in the last hour; the next stops the task as `PACE`.
- `maxStartsPerDay`, the `starts-stopped` file and its `act stopped` cause are removed.

## Capabilities

### Modified Capabilities

- `unattended-work`: a per-task hourly ceiling replaces the daily one; the round count ignores reworked reports.
