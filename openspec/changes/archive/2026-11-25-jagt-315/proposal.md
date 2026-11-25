## Why

A channel whose `webhookEnv` variable was unset refused the whole start, while every other missing optional part lets the start go on.

## What Changes

- An unset `webhookEnv` drops that channel: one `start goes on without` line names the channel and the points told nowhere.
- The board's head marks the dropped channel until a read finds the variable set.

## Capabilities

### New Capabilities

### Modified Capabilities

- `notification-routing`: an unset webhook variable drops its channel instead of refusing the start.
