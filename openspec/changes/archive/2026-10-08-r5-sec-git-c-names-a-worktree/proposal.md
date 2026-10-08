## Why

`git -C <dir>` passed for any directory inside a worktree, so a bare repository the diff committed ran its own config
(`core.fsmonitor`) under `git -C x status`. The reviewer could not run git in a worktree past the first at all.

## What Changes

- `-C` names one of the round's worktrees exactly, nothing inside one.
- The reviewer may run `git -C`, and is told to for every worktree past the first.

## Capabilities

### Modified Capabilities

- `master`: the reviewer's `git -C` names a worktree.
