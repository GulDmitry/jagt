## Why

A deploy conflict's watch (handed once, progress, told) lived beside the deployment on the task, and the stop holding a resolved
conflict was a map in memory, so a restart said it again.

## What Changes

- The watch is the conflict of the target the press stopped at, written only by the deployment's steps; the hold of a stop is kept there too.
- A task leaving `DEPLOY_CONFLICT` clears that conflict through the deploy region of the chart.
- A file written with the old watch, or with a task at `DEPLOY_CONFLICT` and no conflict recorded, reads as a conflict of the record.

## Capabilities

### Modified Capabilities

- `task-flow`: where a handed, told and held conflict is kept.
