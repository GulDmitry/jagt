## Why

`turn-end` refused an unreported turn only where the move is the agent's. A change typed into the terminal at DEPLOYED,
REVIEWED, APPROVED, CI_POLLING or CI_FAILED ended unseen: tree dirty, no round reached the Master, nothing shipped
(seen 2026-10-26).

## What Changes

- A turn that changed the worktree and reported nothing is refused once at every status but REVERTED, DEPLOY_CONFLICT and DONE.

## Capabilities

### Modified Capabilities

- `runtime`: the hook requirement names the tree as a second measure.
