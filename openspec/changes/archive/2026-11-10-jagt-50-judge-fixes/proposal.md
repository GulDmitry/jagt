## Why

Review of the judge-on-board change: `delete-worktree` was barred from the Master though the Master in `act` holds every
verb a human holds; it left a registered orphan in git's list; and an `UNCHANGED_FAILURE` stop ran the verify command a
second time to find its evidence.

## What Changes

- The Master may run `delete-worktree`; the orphan scan bounds what it can delete.
- A registered orphan is removed with `git worktree remove --force` in its repository, one worktree; an unregistered
  directory is deleted as before.
- An `UNCHANGED_FAILURE` stop carries the output of the run that failed, kept when that round was told, never a second run.

## Impact

- Specs: `board-judgement`.
- Code: `DeleteWorktreeCommand`, `WorktreeOrphans`, `VerifyJob`, `ToldOnce`.
