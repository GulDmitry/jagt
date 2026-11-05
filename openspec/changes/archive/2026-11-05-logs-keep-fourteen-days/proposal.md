## Why

A restart replaced `.1`, so a defect seen two runs ago had no log left.

## What Changes

- Each start moves the previous `.1` to `<log>.<UTC yyyy-MM-dd-HHmmss>`; those archives live 14 days and at most 200 MB.

## Capabilities

### Modified Capabilities

- `guards`: a log keeps dated archives.
- `unattended-work`: a restart keeps the runs before.
