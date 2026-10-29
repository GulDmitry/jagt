## Why

A press on a fix worktree nothing was staged in discarded it and answered "nothing to deploy", leaving the task at `DEPLOY_CONFLICT` with no worktree.

## What Changes

- Such a press is refused, saying the deploy waits on the session's fix; the worktree and the status stay.

## Capabilities

### Modified Capabilities

- `infra-wait`: the deploy pipeline requirement gains a scenario.
