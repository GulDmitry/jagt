## Why

With `remote: none`, `git push . HEAD:<target>` is refused whenever a worktree, the base checkout included, holds `<target>`.

## What Changes

- Where nothing is shared, a deploy or revert fast-forwards the worktree holding the target; else it pushes within the repository.

## Capabilities

### Modified Capabilities

- `git`: the deploy and revert write in a repository with no remote.
