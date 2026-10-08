## Why

A revert from DEPLOY_CONFLICT that stopped part way moved the task to DEPLOYED, where nothing resumes the half-merge
still waiting in the deploy worktree. The requirement's sentence on what it refuses was garbled.

## What Changes

- A part-way revert from DEPLOY_CONFLICT keeps DEPLOY_CONFLICT.
- The requirement says plainly what is refused; the conflict cases become scenarios.

## Capabilities

### Modified Capabilities

- `git`: a part-way revert from a conflict keeps the conflict.
