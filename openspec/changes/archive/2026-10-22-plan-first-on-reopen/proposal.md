## Why

Reopening an existing task in `plan` mode left its plan gate open, so a session told to plan first could write source files.
The answer said "PLAN MODE", which jagt no longer launches.

## What Changes

- `open_task_tab` with `mode=plan` sets the task's plan gate to required, as creating a task in `plan` mode does.
- Its answer says the session plans first and the plan is approved on the board with `approve-plan`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `task-flow`: reopening a task in `plan` mode holds its plan gate.
