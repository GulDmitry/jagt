## Why

A multi-repo deploy conflicting on a later repository left an earlier one live, and `revert` was refused there.

## What Changes

- `revert` is allowed from DEPLOY_CONFLICT and takes out what landed.

## Capabilities

### Modified Capabilities

- `git`: revert reaches a deploy stopped by a conflict.
