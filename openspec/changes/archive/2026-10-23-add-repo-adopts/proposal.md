## Why

A session that cut a second repository's worktree itself left jagt two ways to lose its work: add-repo would recreate
a branch holding no commits yet, and a failed attach would remove a worktree jagt never cut.

## What Changes

- `add-repo` registers a worktree that already stands on the task branch in that project instead of cutting one.
- A failed attach leaves such a worktree, and its uncommitted work, where it is.

## Capabilities

### New Capabilities

### Modified Capabilities

- `git`: add-repo adopts a worktree already on the task branch.
