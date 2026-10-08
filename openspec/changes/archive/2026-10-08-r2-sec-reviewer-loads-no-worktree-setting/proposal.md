## Why

The reviewer loaded the task worktree's project and local settings: allow rules and hooks the diff under review can
write, and jagt's own hooks, which reported the reviewer's start and end as the task's session. Its transcript landed
where `--continue` revives the task's session.

## What Changes

- The reviewer loads the human's user settings alone and persists no session.
- It is refused `git -c` and `git --config-env`, which can name an external diff.

## Capabilities

### Modified Capabilities

- `master`: the reviewer loads no worktree setting and leaves no session.
