## Why

The first `masterEval` run with the read-gate check failed nearly every case on "no refusal": the gate refuses a few
calls in every round and the Master reads on. A refusal is the gate working, and no requirement promised none.

## What Changes

- `masterEval` asserts only that the read gate heard a call naming the run's worktree.

## Capabilities

### New Capabilities

### Modified Capabilities

- `judgement-resilience`: a refused call no longer fails a run.
