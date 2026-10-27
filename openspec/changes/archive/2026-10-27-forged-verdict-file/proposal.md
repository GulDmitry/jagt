## Why

A task session writes freely inside its worktree, so it could write `.jagt/master-review.json` itself: a ready
verdict naming the round in front, which the Master in `act` acted on and shipped, no role having read anything.

## What Changes

- jagt records the SHA-256 of each verdict file it writes in the task; a file of any other bytes is no verdict.
- A review no role read is a question for the human, never ready.

## Capabilities

### Modified Capabilities

- `master`: the round-read scenario: a file jagt did not write answers no round.
