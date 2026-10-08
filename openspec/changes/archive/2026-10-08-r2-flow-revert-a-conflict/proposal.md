## Why

`revert` from a DEPLOY_CONFLICT where nothing had landed refused before discarding the half-merge, a dead end; and it
discarded the half-merge before the walk-back, so a refused revert lost the human's resolution.

## What Changes

- From a DEPLOY_CONFLICT with nothing landed, `revert` discards the half-merge and lands REVERTED.
- The half-merge is discarded only after everything that landed is out.

## Capabilities

### Modified Capabilities

- `git`: when `revert` discards a deploy conflict's half-merge.
