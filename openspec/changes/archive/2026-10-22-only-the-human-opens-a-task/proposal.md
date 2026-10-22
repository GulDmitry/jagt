## Why

In `act` two answers to one session carried the same `do` line; the Master opened and shipped two tasks for one change.

## What Changes

- The Master opens no task; a `do` line in its answer opens nothing and the session is told only the human opens one.
- `MasterOpen` and `MasterShip.open` are removed; work the task needs is done on its own branch.

## Capabilities

### Modified Capabilities

- `master`: a task's work stays in the task; only the human opens one.
- `unattended-work`: the `master` window has no task opening to show.
