## Why

Reopening a task in plan mode closed its plan gate at any status. Past the code the gate refused every source write, and
a plan cannot be reported from there, so the only exit was unreachable.

## What Changes

- Reopening a task in plan mode is refused where a plan can no longer come first.

## Capabilities

### Modified Capabilities

- `task-flow`: Plan first is a gate jagt holds gains a scenario.
