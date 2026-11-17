## Why

The nudge's Enter retry waited a fixed 700 ms in real time; every test of it slept, and a pitest mutant in that loop
timed out at the sum of those sleeps (JAGT-119).

## What Changes

- `orchestrator.agent.submitWait` (default `700ms`, `0` no wait) is the wait before a nudge left in the input gets Enter again.
- `orchestrator.agent.draftPoll` (default `1s`, `0` no wait) is how often a nudge looks again while the human types.

## Capabilities

### Modified Capabilities

- `master`: the wait between a nudge's Enter presses.
