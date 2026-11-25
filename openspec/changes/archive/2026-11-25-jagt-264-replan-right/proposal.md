## Why

Approving a task's first plan and approving a later one were one right, `plan`, so a human who wanted the first plan only could not hand the Master the later ones.

## What Changes

- `roles.master.mine` gains `replan`: every plan after a task's first approved one. `plan` is the first.
- Unset `mine` is `[plan, replan, deploy, revert]`; a plan opened in `plan` mode stays the human's.
- The approve-plan button's hover says which plan waits.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: the plan approval is two rights, `plan` and `replan`.
