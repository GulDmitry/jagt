## Why

Deploy and revert worktrees were still cut beside the repository, so the data folder did not hold everything jagt writes and a repository's parent had to stay writable.

## What Changes

- A deploy or revert worktree is cut at `worktrees/<project>/<task>-deploy` and `-revert` under the data folder; nothing is written beside the repository.
- Each project has its own deploy path, so siblings no longer derive one.
- A live task's deploy and revert worktrees are not orphans; those of a gone task are.
- The start no longer refuses a repository whose parent is read-only.

## Impact

- `startup`: "One data folder holds everything jagt writes" names deploy and revert worktrees.
- `git`: "Siblings share one deploy path" becomes "Deploy and revert worktrees live under the data folder".
