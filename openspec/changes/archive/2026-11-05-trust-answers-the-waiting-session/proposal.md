## Why

A session that asked the human to trust its changed git config waited forever: the trust took, nothing told it.

## What Changes

- `trust` on a parked task whose session asked tells it `answered`: retry what the guard refused.

## Capabilities

### Modified Capabilities

- `git-trust`: trust answers the session waiting on it.
