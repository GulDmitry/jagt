## Why
The once-per-arrival guard of `DeployOnApprovalJob` lived in memory: a restart forgot a refused arrival and pressed again.

## What Changes
- The guard is a mark on the task's turn (`state.json`), keyed by the arrival's status stamp.

## Impact
- deploy-on-approval
