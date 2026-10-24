## Why

What the Master may press or send, and at which status, lived in its jobs' code alone: no human could read it, and
nothing refused a move the Master made where no rule meant it (decision 0073).

## What Changes

- `master-cases.md.dist` holds one case per Master move, a verb it presses or a step it relays, with the statuses it
  is made at; a human copy `master-cases.md` overrides it case by case.
- Startup refuses a case naming a move the Master lacks, a move no case names, and a case it cannot read.
- A Master move at a status no case allows is not made: it is written to `misses.jsonl`, `by: master`, and the task
  stops for the human.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: the Master's moves join the session's steps as cases.
