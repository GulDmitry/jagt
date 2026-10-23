## Why

A `ready` verdict the Master presses on made the card yours until the next tick pressed it, so every round it shipped
pinged you.

## What Changes

- A move pings only once it has stood five seconds (`YourMoveJob`); one a machine presses meanwhile pings nobody.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: the ping waits for the move to settle.
