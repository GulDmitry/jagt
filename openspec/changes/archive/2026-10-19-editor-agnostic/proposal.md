## Why

The editor seam named JetBrains: its recent-projects list, its folders, a job pruning it.

## What Changes

- The editor driver only opens a worktree and a diff, through `editorCommand` and `editorDiffCommand`.
- The IDE service folders a fresh worktree needs are copied at the worktree level, whichever IDE wrote them.
- The `idecleanup` job is removed.

## Capabilities

### Modified Capabilities

- `runtime`: the editor is a launcher.
- `unattended-work`: the tidy jobs no longer include `idecleanup`.
- `board-workspace`: Run now is shown on a job that exists.
