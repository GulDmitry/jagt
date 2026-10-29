## Why

A deploy whose pipeline failed on the merge result had no one to fix it: the session works in its own worktree, where the defect does not exist, and only `deploy` writes the deploy branch.

## What Changes

- A deploy pipeline red on the code hands the deploy worktree to the session as a deploy conflict is handed.
- A fix staged there is committed and pushed by `deploy`, and the pipeline is read again on the commit pushed.

## Capabilities

### Modified Capabilities

- `infra-wait`: the deploy pipeline requirement gains the red-on-the-code scenarios.
