## Why
A rejected deploy push, a diverged branch, a revert with no recorded merge and a conflict outside `act` ended in text
telling the human to run git. The task's session has the context and the worktree.
## What changes
- `hand-off <ticket>` says the task's git case to its session: branch, shas, the command to run. Judge and act alike.
- A revert with no recorded merge lists candidate merge commits (git, newest first, at most 10); `revert <ticket> <sha>` takes the pick out.
- The refusal sentences name the facts and the verb; none says "by hand" or "yourself".
