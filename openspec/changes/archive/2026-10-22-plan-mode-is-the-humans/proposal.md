## Why

A task opened in `plan` mode, so the human could read the plan, was approved by the Master in `act` and shipped as
a request holding only a review document. Each restart emptied jagt's logs, so the run before left no evidence.

## What Changes

- The Master's `go` opens only a plan gate autoplan closed; a `plan`-mode plan waits for `approve-plan`.
- `master.mine` unset is `[plan]`.
- Each start rolls the backend log and the Master's into their one `.1`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: in act a plan asked for in `plan` mode stays yours; `mine` unset keeps `plan`.
- `task-flow`: the Master's `go` opens only an autoplan gate.
- `unattended-work`: a restart keeps the run before.
- `guards`: a log also rolls at start.
