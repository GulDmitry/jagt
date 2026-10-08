## Why

A session could report its own task `REVIEWED` or `APPROVED`, and in `act` the Master then deployed an unreviewed
branch.

## What Changes

- A session reports neither verdict; only jagt's read of the round on the host lands one.
- The status tool offers a session only the statuses it may report.

## Capabilities

### Modified Capabilities

- `task-flow`: a verdict comes from the round read alone.
- `protocol`: the status enum is what a session may report.
