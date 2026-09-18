# Git safety

[← AGENTS.md](../../AGENTS.md)

## The only writes to a shared branch

- `deploy`: `origin/<task>` → `deployBranch` (`mergeIntoAndPush`). `revert`: its recorded merge commit
  (`revertMergeAndPush`). Master-only, via `deployTarget`.
- `do <ticket> from <branch>` (`TaskState.baseBranch`) moves the cut and the request's **target**, never the
  merge destination (`baseBranchOr`); `deployTask` **refuses** the two being equal.
- `revert` refuses rather than guess: no `deployCommit`, commit absent, already reverted, conflict.
- `detachUpstream` unsets the inherited `origin/<baseBranch>` at creation.
- `pushBranch`: **one** branch, both-sided refspec, never `--force`, never `-u`. Nothing rewrites what has left
  the machine (sub-agent rule 8) — `commit --amend` and `reset --hard` onto a pushed commit refused alike; the
  ONE exception is the resume rebase below.
- Per-repository lock on every git call: several sessions share one checkout.
- **No verdict gates `deploy`**: `Move.deployable` asks only for an open request, plus DEPLOY_CONFLICT;
  NEW, SHIPPING, IN_PROGRESS, REVERTED and DONE are not.
- The confirm's `project → branch` line comes from `RepoView.deployBranch`; `revert` names its **scope**: the
  last deploy.

## What a commit, a ship and a worktree carry

- **A commit carries the task's work, never jagt's plumbing**: `commitAll` stages all, unstages
  `WorktreeFiles.GENERATED`. A modified `AGENTS.md` is the agent's.
- **`ship` hands the work to the agent, every repository at once**: ONE `ShipService` instruction — commit,
  push, open or update a request per repository against its own target, report every link in one
  `update_agent_status` (`reviewRequests`, one URL per project). jagt runs no git, calls no host; the task
  waits in SHIPPING.
- **A ship approves ONE commit**: only the next relay replaces `task_context.md` (`writeTaskContext`
  truncates, `relayIfChanged` skips an identical brief). Re-reading it is no permission.
- **A resumed branch is put on what origin holds, then replayed on its target** (`rebaseOntoTarget`):
  fast-forwarded when behind, REFUSED when both sides carry commits, left alone when only this machine's do.
  The replay is pushed back under a lease, a refused lease undoing it; a CONFLICTING rebase stands in the
  worktree for the session. One this machine never had comes from `origin/<branch>`, not the request's target.
- **A branch the base repository holds is freed, not refused** (`freeCheckout`): detached **in place**, never
  before the strategy switch, ignoring **untracked** files. Tracked changes, or another worktree holding it,
  stay refusals.

## No git hook in a repository — jagt's own live in the worktree

- Two layers, one refusal — a push whose destination is not the task's branch: `ToolGate`
  (`POST /api/agent/tool`, the command LINE) and `.jagt/hooks/pre-push` (`WorktreeHooks`, the refs git writes).
- **Kept out of the project**: `core.hooksPath` via `GIT_CONFIG_*` on the launch command (`WorktreeHooks.gitEnv`
  in `TmuxSessionHost`) — that session and its children, no repository config written. `deploy` and `revert`
  run ungated.
- Pointing git elsewhere REPLACES the repository's hooks, so **every name git knows** gets a stub running the
  repository's own, re-resolved at run time with the override off, guard first.
- **Pushes only**, everything else passes; `--no-verify` skips this hook as any, and a human's shell is never
  gated.

## One session, many repositories

- What multiplies is **worktrees**, never agents: `task/TaskRepo` is a list, `repos.get(0)` runs the session;
  `done` deletes every one.
- Creation is all-or-nothing (`resolveRepos`), unwinding what was cut; a task's repositories are one scope
  (`findByWorktree`).
- **The round is merged as the least finished repository** (`ReviewSweepService.merged`): approved only when
  all are, the pipeline the **worst**, each comment prefixed with its repository.
- **`deploy` lands in order, stops at the first conflict**, naming both sides from where it stopped. Every
  repository is checked deployable before the **first** push.
- Siblings derive one deploy path (`<taskId>-deploy`): `hasDeployWorktree` asks git who cut it,
  `mergeIntoAndPush` **refuses** another repository's, and only DEPLOY_CONFLICT resumes.
- **Every press starts the merge over** unless the human's own work sits there — staged, committed or part
  done, a committed one only with its base on the target.
- Editor residue is **deleted** (`clearEditorResidue`); anything else left and named
  (`StaleDeployPathException`).
- **Nothing to deploy is not a failure** (`NothingToDeployException`): passed over and named.
- `revert` walks back over the repositories holding a merge commit, each **forgetting** it; REVERTED once all
  that landed is out. Both half-states **stamped on the task**.
