## Why

The scenario "The repository a deploy waits on closed" drops a landed repository from the deployment's record, so `revert` misses live code; "A repository already landed" says it stays.

## What Changes

- A target that landed stays in the deployment's record when its repository leaves the task; only a target not yet landed leaves it.
- The repository whose pipeline went red is landed, so it stays, and with it the task and the revert owed.

## Capabilities

### Modified Capabilities

- `task-flow`: the repository a deploy was handed back on leaves with its closed request, a landed one excepted.
