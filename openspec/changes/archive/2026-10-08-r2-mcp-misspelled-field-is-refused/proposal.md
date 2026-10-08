## Why

An unknown field is ignored, so `task_id` in place of `taskId` ran the call on the caller's own task.

## What Changes

- A field the message does not declare is still ignored, unless it folds (case, `_`, `-`) to a declared field the
  call left out: then the call is refused, naming the field it meant.

## Capabilities

### Modified Capabilities

- `protocol`: a misspelled field is refused.
