## Why

The deploy check remembers what it asked in memory only, so a restart asks every `DEPLOYED` task again; the spec
promised once per deploy.

## What Changes

- The spec says once per deploy commit and jagt run.

## Capabilities

### Modified Capabilities

- `task-flow`: the deploy check's once is per run.
