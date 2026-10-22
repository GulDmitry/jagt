## Why

A green verification reported `REVIEW_PENDING` through the door a session's hand-back takes, which sends a hand-back
owing verification to `VERIFYING`. A task with probes or a `verifyCommand` was verified again every 20 seconds and
never reached the human or the Master.

## What Changes

- A verification run's verdict lands through its own door, `AgentStatusReports.verified`, which owes no further run.

## Capabilities

### New Capabilities

### Modified Capabilities

- `task-flow`: a green verification run lands `REVIEW_PENDING`.
