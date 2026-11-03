## Why

A request the human closed unmerged on the host, for the repository a deploy was handed back on for its pipeline, left
the task in `DEPLOY_CONFLICT` for good: nothing read the host while the conflict stood.

## What Changes

- While a deploy stands handed back for its pipeline, the conflicted repository's request is read every minute,
  whatever the Master's mode; closed unmerged, that repository leaves the task and the deployment's record.
- With no conflicted target left the task lands `DEPLOYED` on the others, whose deploy check goes on.
- A task's only repository, and a conflict of a merge or a revert, are unchanged.

## Capabilities

### Modified Capabilities

- `task-flow`: a closed request takes its repository out of the task, the one a deploy waits on included.
