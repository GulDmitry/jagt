## Why

A task done twice in one day overwrote its first snapshot, and the kept store was bounded by a task count that says nothing about disk.

## What Changes

- `done` writes one snapshot per call to `archive/<yyMMdd-HHmmss>-<task>/`: the documents and `finished.json`, the task's finished record.
- The finished record holds the task's `usage` and `agentUsage` breakdowns and the paths of the session logs it was read from.
- `archive/` is bounded by `archive.maxMegabytes` in `jagt.yml` (default 20); past it the oldest entries go first.
- The 1000-directory zip packing is removed.

## Capabilities

### Modified Capabilities

- `guards`: the artifacts store is a byte-bounded archive.
