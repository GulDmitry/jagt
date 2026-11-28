## Why

A checking role's report and the scripted flows' evidence, written into a task's worktree, showed in `git status` and were committed with the task's work.
They were outside `Artifact`, the one list of what jagt writes into a worktree.

Kind: unit tests, worktree plumbing and finish archive; the Artifact list in the task domain
Holds: today's architecture; `Artifact` is already the one list its two readers share, it lacked two entries
Written: 22aea4efc
Uncaught: no test ran the git exclude or the archive against a role report or the evidence folder, so a name written outside the list passed unseen
Unseen: a role's report is untracked, so a commit sweeping the worktree took it without any refusal
Prevents: WorktreeFilesTest and FinishedArtifactsTest pin the role report and the evidence folder in the exclude and in the archive

## What Changes

- `Artifact` lists a role's report (`<role>-report.md`) and the evidence folder (`acceptance/`).
- Both stay out of `git status` in every worktree and are archived on finish.

## Capabilities

### Modified Capabilities

- `acceptance-workspace`: where a round's report and evidence live and what becomes of them.
