## Why

In `act` two answers to one session carried the same `do` line; the Master opened and shipped two tasks for one change.

## What Changes

- The Master opens no task; a `do` line in its answer opens nothing and the session is told only the human opens one.
- `MasterOpen` and `MasterShip.open` are removed; the Master's rule names the work for the closing report instead.

## Capabilities

### Modified Capabilities

- `master`: another branch is another task, opened by the human.
- `unattended-work`: the `master` window has no task opening to show.
