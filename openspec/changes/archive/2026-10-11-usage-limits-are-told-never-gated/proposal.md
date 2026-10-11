## Why

A session running a subscription window out moves onto paid usage unseen: money flows and nobody is told.

## What Changes

- A `usage` job reads each window and paid usage through the agent CLI's own login, every 5 minutes by default.
- A banner tells a window crossing a threshold, the first paid spend, and paid usage crossing a share of its limit.
- `stats` opens with the windows, their resets and the paid spend.
- Nothing refuses or slows a task on what it read.

## Capabilities

### New Capabilities

- `usage-limits`: the subscription's limits, read, shown and told.
