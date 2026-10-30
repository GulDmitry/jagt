## Why

A revert, the fix worktree of a red pipeline and a conflict hand-off read the deploy branch from `jagt.yml`, so a branch moved since the press sent them to a branch the merge is not on.

## What Changes

- The branch the deployment record holds for a target is the one revert, the conflict and the fix worktree use; the configured one stands where the record holds none.

## Capabilities

### Modified Capabilities

- `revert`: a revert goes to the branch the merge landed on.
