## Why

A deploy that broke off left an earlier repository live with no `revert` offered, and a revert from DEPLOY_CONFLICT
left the conflicted half-merge waiting in the deploy worktree.

## What Changes

- `revert` is offered wherever a repository records a merge no revert took out.
- A revert from DEPLOY_CONFLICT discards the half-merge waiting in the deploy worktree.

## Capabilities

### Modified Capabilities

- `git`: revert reaches every live merge and leaves no conflict behind.
- `task-flow`: the guard reads whether a deploy is live.
