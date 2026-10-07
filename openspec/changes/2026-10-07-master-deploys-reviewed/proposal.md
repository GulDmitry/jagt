## Why

`master.mine` promised the Master every step not listed, deploy included, yet nothing pressed it; and a session's
report pulled a task out of `DEPLOY_CONFLICT`, so its staged resolution was never deployed and the button was gone.

## What Changes

- In `act`, with `deploy` not in `mine`, the Master deploys a `REVIEWED` task once (`MasterDeployJob`).
- A report holds a `DEPLOY_CONFLICT` task until the deploy finishes.

## Capabilities

### Modified Capabilities

- `master`: deploys a `REVIEWED` task.
- `task-flow`: `DEPLOY_CONFLICT` is held against a report.
