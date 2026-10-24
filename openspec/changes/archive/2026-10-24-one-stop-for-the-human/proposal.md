## Why

A repeating Master round ended two ways: two rounds of only repeats became a question, which in `act` the Master
answered itself, and every round counted toward the round ceiling's stop. Five names wrote or read one stop.

## What Changes

- A round of only repeats is relayed `[repeat]` and counts toward the round ceiling like any round; no question.
- The ceiling is `loopBreaker.rounds` in `jagt.yml` (default 3).
- The stop stands on the task (`TaskState.stop`), `TaskStops` its one writer; the trail keeps rounds only.

## Capabilities

### New Capabilities

### Modified Capabilities

- `unattended-work`: the round ceiling is configured and takes repeats; the stop's writer and guard are renamed.
- `master`: repeats end at the round ceiling, not in a question.
- `review`: the round digest lives in `TaskStops`.
