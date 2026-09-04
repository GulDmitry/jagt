# Git safety

[← AGENTS.md](../../AGENTS.md)

## The only writes to a shared branch

- `deploy` merges the task branch into `deployBranch` (`GitService.mergeIntoAndPush`), `revert` reverts the
  merge commit it recorded (`revertMergeAndPush`); both Master-only, via `deployTarget`.
- A per-task base (`do <ticket> from <branch>` → `TaskState.baseBranch`) moves what the worktree is cut from
  and what the request **targets**, never a merge destination; `deploy` stays on `deployBranch`
  (`TaskState.baseBranchOr`), and `deployTask` **refuses** when the two are equal.
- `revert` refuses rather than guess: no `deployCommit`, the commit absent, already reverted, or a conflict —
  aborted and cleaned up.
- `GitService.detachUpstream` unsets the inherited `origin/<baseBranch>` right after creation.
- `GitService.pushBranch` pushes **one** task branch, both-sided refspec, never `--force`, never `-u`;
  nothing rewrites what has left the machine (sub-agent rule 8) — `--force-with-lease`, `commit --amend` and
  `reset --hard` onto a pushed commit are refused alike.
- Every git operation runs under a per-repository lock (`GitService`): several sessions share one checkout.
- **A reviewer's verdict is no gate on `deploy`**: `Move.deployable` asks only whether a request is open, plus
  DEPLOY_CONFLICT; NEW, SHIPPING, IN_PROGRESS, REVERTED and DONE are excluded.
- The confirm's `project → branch` line per repository comes from `TaskView.RepoView.deployBranch`; `revert`
  names its **scope**: the last deploy only.

## What a commit, a ship and a worktree carry

- **A commit carries the task's work, never jagt's plumbing**: `commitAll` stages everything, then unstages
  `WorktreeFiles.GENERATED` whatever the checkout. A modified `AGENTS.md` is the agent's.
- **`ship` hands the work to the agent, in every repository at once**: ONE `ShipService` instruction — commit,
  push the task branch, open or update a request **per repository** against its own target, then report the
  links in one `update_agent_status` (`reviewRequests`, one URL per project). jagt runs no git and calls no
  host; the task waits in SHIPPING.
- **A ship approves ONE commit, and `task_context.md` never says so**: only the next relay replaces it
  (`writeTaskContext` truncates, `relayIfChanged` skips an identical brief) — reading it again is no
  permission.
- **A branch the base repository still holds is freed, not refused** (`GitService.freeCheckout`): detached
  **in place**, never before the strategy switch, ignoring **untracked** files. Tracked changes, or a branch
  held by **another** worktree, stay refusals.

## No git hook in a repository — jagt's own live in the worktree

- Two layers answer one refusal, a push whose destination is not the task's own branch: `ToolGate`
  (`POST /api/agent/tool`, the command LINE) and `<worktree>/.jagt/hooks/pre-push` (`WorktreeHooks`, the refs
  git will write).
- **The mechanism keeps it out of the project**: `core.hooksPath` set by `GIT_CONFIG_*` on the launch command
  (`WorktreeHooks.gitEnv`, applied in `TmuxSessionHost`) — that session and its children, no repository config
  written. `deploy` and `revert` run outside it, ungated.
- Pointing git elsewhere REPLACES the repository's hooks, so **every name git knows** gets a stub running the
  repository's own, re-resolved at run time with the override off, the guard first.
- **Pushes only**: everything else passes; `--no-verify` skips this hook as any, and a human's shell is never gated.

## One session, many repositories

- What multiplies is **worktrees**, never agents: `task/TaskRepo` is a list, `repos.get(0)` where the session
  runs; `done` deletes every one.
- Creation is all-or-nothing (`TaskProvisioning.resolveRepos`), unwinding what was cut; a task's repositories
  are one scope (`StateService.findByWorktree`).
- **The review round is merged** as the least finished repository (`ReviewSweepService.merged`):
  approved only when all are, the pipeline the **worst** one, each comment prefixed with its repository.
- **`deploy` lands in order and stops at the first conflict**, naming both sides from **where the sequence
  stopped**. Every repository is checked deployable before the **first** push.
- Siblings derive one deploy worktree path (`<taskId>-deploy`): `GitService.hasDeployWorktree` asks git who
  cut it, `mergeIntoAndPush` **refuses** another repository's, and only DEPLOY_CONFLICT resumes.
- **Every press starts the merge over** unless a resolution is staged or committed there: an unresolved or
  aborted worktree is cut again from the target as it is NOW.
- Editor residue there is **deleted** (`clearEditorResidue`); anything else is left and named
  (`StaleDeployPathException`).
- **Nothing to deploy is not a failure** (`GitService.NothingToDeployException`): passed over and named.
- `revert` walks back in reverse over the repositories holding a merge commit, each **forgetting** it;
  REVERTED only once everything that landed is out. Both half-states are **stamped on the task**.
