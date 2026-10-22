## Why

In `act`, a task cycled for an hour: each ship opened a round, each poll relayed the same bot threads to a fresh
session, the session answered `no_changes`, and the Master shipped again. Five ships, no guard. The relay's dedup
compared the brief with `task_context.md`, which the ship instruction had overwritten in between.

## What Changes

- A round identical to the last one relayed is never relayed again, whatever was written in between.
- A deterministic loop stop trips on stalled rounds or on a ship past the ceiling; only the human's `lift` clears it.

## Capabilities

### New Capabilities

### Modified Capabilities

- `unattended-work`: jagt stops a task it suspects it is looping on.
- `review`: an unchanged round is relayed once.
