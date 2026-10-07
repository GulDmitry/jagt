## Why

In `act` the Master closed a task whose session the human was still questioning, killing that session
([0016](../../../docs/decisions/0016-only-the-human-closes-a-task.md)).

## What Changes

- A `ready` round holding nothing to ship waits for the human; the Master never closes a task.
- `done` is no longer a step `master.mine` can name.

## Capabilities

### Modified Capabilities

- `master`: the Master no longer closes a task in `act`.
