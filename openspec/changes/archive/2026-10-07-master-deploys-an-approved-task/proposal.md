## Why

A request approved on the host before the deploy job's tick sat at `APPROVED` in `act`, never deployed; and nothing
refused a `done` the Master pressed, so "closing stays the human's" held only by habit.

## What Changes

- In `act` the Master deploys an `APPROVED` task as it does a `REVIEWED` one.
- A `done` from the Master is refused (`TaskAction.humanOnly`).
- The scenario names `mine: [deploy, revert]`, the only pair the start accepts.

## Capabilities

### Modified Capabilities

- `master`: deploys `APPROVED` too; its `done` is refused.
