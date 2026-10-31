## Why

A QA read was no turn: the board showed nobody while it ran, and a restart in the middle of one left no trace to run it again from.

## What Changes

- QA holds the DEPLOYED turn while its check reads, and closes it once the run answered.
- A check whose turn stands open and unanswered after a restart runs again on the same sha.

## Capabilities

### Modified Capabilities

- `qa`: who holds the turn while a check reads, and what a restart does to an open one.
