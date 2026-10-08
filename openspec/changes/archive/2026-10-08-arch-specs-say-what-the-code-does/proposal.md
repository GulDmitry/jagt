## Why

The startup spec said only `projects` is re-read live, while every section the config service reads is. The task
cap, the Master's kept-step vocabulary, six jobs, `run <job>` and the event stream had no spec at all.

## What Changes

- The live sections are named; `agent.cli`, `tracker.workflow` and the rest need a restart.
- The 24-task cap, the `master.mine` start check, the quiet jobs and `run <job>` are specified.
- The board's event stream names its route.

## Capabilities

### Modified Capabilities

- `startup`: which keys are live; the start refuses a bad `master.mine`.
- `task-flow`: at most 24 tasks.
- `master`: kept steps come from `task/MasterRight`.
- `unattended-work`: the quiet jobs and `run <job>`.
- `board`: the event stream at `/api/events`.
