## Why

Each eval case ran once, so one flake read as a regression: a prompt change took four runs on the change and four on
the base to judge. A case also asserted only the model's words, never what jagt heard of the run.

## What Changes

- An eval case passes when a majority of its runs do (`EvalQuorum`, `-PevalRuns`, `-PevalPasses`).
- `masterEval` also asserts the read gate heard the round's worktree and refused nothing.
- A pattern line in the Master brief states one fact answerable yes or no on the diff.

## Capabilities

### New Capabilities

- `judgement-resilience`: how jagt holds a model's answer steady in its own evals.

### Modified Capabilities
