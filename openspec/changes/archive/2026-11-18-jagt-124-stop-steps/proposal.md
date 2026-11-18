## Why

A stop review walks back to the step where the error entered, but `stops.jsonl` kept only the trigger and its evidence,
and the task has left `state.json` by the time the review runs (JAGT-124).

## What Changes

- Each `stops.jsonl` line also carries `steps`: the task's history and trail entries up to the stop, newest last,
  each capped at the trail's own cap of 20.
- `loop-review` reads the steps from the stop line once the task has left `state.json`.

## Capabilities

### Modified Capabilities

- `task-stops`: a stop line carries the task's steps.
