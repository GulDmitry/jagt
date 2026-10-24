## Why

On a stand run 24 jagt processes launched sessions at once and 9 sat on Claude's trust dialog for ten minutes: each
process read, edited and replaced `~/.claude.json` under a lock only its own JVM held, so one process's write lost
another's flag.

## What Changes

- Every jagt process takes one OS file lock beside the config around its read, edit and replace.

## Capabilities

### New Capabilities

### Modified Capabilities

- `runtime`: the worktree's trust survives several jagt processes writing at once.
