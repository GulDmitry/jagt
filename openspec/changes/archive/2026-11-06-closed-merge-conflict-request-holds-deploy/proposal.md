## Why

A request the human closed unmerged on the host, for the repository a deploy was handed back on for a merge conflict,
left the task in `DEPLOY_CONFLICT` for good: only a pipeline hand-back was read.

## What Changes

- While a deploy stands handed back for a merge conflict, the conflicted repository's request is read every minute too;
  closed unmerged, that repository leaves the task, its deploy worktree removed, and the deployment's record.
- The repositories after it were never pushed: the task stays `DEPLOY_CONFLICT` with `deploy` offered, and the press
  lands them after the ones already landed, so `revert` still covers those.
- With nothing left to push and a repository landed, the task lands `DEPLOYED`. A conflict of a revert is unchanged.

## Capabilities

### Modified Capabilities

- `task-flow`: a closed request takes its repository out of the task, the one a deploy waits on for its merge included.
