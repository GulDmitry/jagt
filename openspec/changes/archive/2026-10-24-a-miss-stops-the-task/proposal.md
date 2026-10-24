## Why

A miss was written to `misses.jsonl` and logged, and nothing put it in front of the human (decision 0073).

## What Changes

- A miss stops the task through the loop stop: the card asks `lift the stop`, its hover reads the miss and who set
  it off, and the Master's jobs leave it.
- The card's hint names no cause; the hover carries it, with or without a review request.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: a miss stops the task until a human lifts it.
