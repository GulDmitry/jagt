# Git safety

[← AGENTS.md](../../AGENTS.md)

## Git safety

### The only writes to a shared branch

`deploy` (task branch → `deployBranch`, via `GitService.mergeIntoAndPush`) and its undo `revert`
(`revertMergeAndPush`: reverts the merge commit deploy recorded — **adds** a commit, never rewrites history,
never force-pushes). Both are Master-only and both go through `deployTarget`, so they share one deployBranch
guard.

`ship` creates or updates a merge **request** only. Never merges.

`revert` refuses rather than guess in every ambiguous case: no recorded merge commit (a deploy from before
`deployCommit` existed — the human gets the by-hand `git revert -m 1` recipe, jagt will **not** search the
log), the commit is not on the branch, it was already reverted, or the revert conflicts (aborted and cleaned
up; unlike a deploy conflict there is no half-state worth keeping).

### The base branch is read-only

`baseBranch` is what tasks are cut from, and nothing ever pushes or merges to it. That holds for a per-task
base too (`do <ticket> from <branch>`, persisted as `TaskState.baseBranch`): it moves what the worktree is cut
from and what the merge request **targets**, never what anything merges into.

`deploy` stays on `deployBranch` whatever a task's base is. Read the effective base through
`TaskState.baseBranchOr(project.baseBranch())`, so the worktree, the MR target and `ide … diff` cannot drift
apart. `deployTask` **refuses** when `deployBranch` equals `baseBranch`.

Sub-agents are forbidden by prompt rule from pushing or merging anywhere but their own task branch. A worktree
branch is cut from `origin/<baseBranch>` and inherits it as upstream, so `GitService.detachUpstream` unsets it
right after creation — a bare `git push` then errors ("no upstream") instead of pushing the task branch
straight into the release branch.

`GitService.pushBranch` pushes **one** task branch with an explicit both-sided refspec: never `--force`, never
`-u` (an upstream is the trap `detachUpstream` removes).

### What a reviewer said is not a gate on `deploy`

The owner's call, 2026-08-18. `Move.deployable` asks only whether a request is open — plus DEPLOY_CONFLICT,
which is finished by deploying again — because deploy merges the task **branch**, and git's only precondition
is commits on it. Gating the button on REVIEWED/APPROVED meant a human looking at a REVIEW_PENDING card could
not land a request they had decided to land.

What stays excluded is what could only race or refuse:

| status | why |
|--------|-----|
| NEW | nothing on the branch |
| SHIPPING | a push in flight |
| IN_PROGRESS | an agent committing **into** the branch this would merge |
| REVERTED | a revert adds a commit, so the branch holds nothing the deploy branch lacks |
| DONE | closed |

### The confirm names the writes and nothing else

The board names the writes it is asking for before it makes them — `deploy` and `revert` alike, one
`project → branch` line per repository, read from `TaskView.RepoView.deployBranch`, because "the deploy branch"
is not something a human can check. `revert` names its **scope** too: only the last deploy comes out.

**That is all either confirm says.** The deploy one advises nothing about the round (the owner's instruction,
2026-08-21): it used to warn that a REVIEW_PENDING round was never shipped and that a `review_replies.md` was
still in the worktree, and both were wrong often enough to train a human to click the dialog away — which costs
the branch lines the only reader they had.

A deploy is the human's to make at any moment; jagt states the writes and gets out of the way. **Do not add a
warning, a badge or a gate to that question.**

### No bulk branch cleanup

A decision, not an omission. `prune [all]` (a cross-project sweep of local branches merged into `deployBranch`)
was built and then **removed on the owner's instruction**: branch cleanup belongs to the one task it concerns,
and a human who wants a branch gone has git.

Do not reintroduce a prune verb, a "merged branches" report, or a board button for either.

### A commit carries the task's work, never jagt's own plumbing

`commitAll` stages everything and then unstages what jagt writes into a worktree regardless of the checkout
(`WorktreeFiles.GENERATED`).

`info/exclude` answers only for **untracked** files, so a project that versions one of those names — jagt
versions `.mcp.json` — used to ship the copy written for that worktree, absolute caller path and all, and every
other clone then read a header pointing at somebody else's directory.

The names jagt **refuses to overwrite** are deliberately not on that list: a modified `AGENTS.md` is the agent's
work. The price is that jagt's own generated files can only be changed in this repository by a human commit.

### A branch the base repository still holds is freed, not refused

`GitService.freeCheckout`. Git allows one checkout per branch, nobody works in the base repository, and a task
blocked on a checkout nobody remembers making is worse than a WARN naming what it was on.

Four things that are not incidental:

- It detaches the repository **in place** — no other ref, so the files an editor has open do not change under
  it, and a per-task base with no local branch is no obstacle.
- It runs **inside** the recreate/resume arms, never before the strategy switch, because a refusal must leave
  the repository where it was.
- The detach is **undone** when what it was freed for does not land, in `createWorktree` and again in
  `TaskProvisioning`'s unwind (a resumed branch survives, so there is something to go back to).
- It ignores **untracked** files, since only tracked changes are carried.

Two cases stay refusals: tracked changes in that checkout, and a branch held by **another** worktree.

### `ship` hands the work to the agent, in every repository at once

`ShipService` writes ONE instruction: commit, push the task branch and open or update a review request **per
repository**, each against that repository's own target, then report every link back in one
`update_agent_status` call (`reviewRequests` carries one URL per project, and it stays one round). jagt runs no
git and calls no host — the agent has the code-host tools, jagt has none — and the task waits in SHIPPING until
the links arrive.

Naming every repository in the instruction is what makes it safe: a repository left out is a half-shipped task,
and jagt cannot tell which half from the outside.

### All git ops under a per-repository lock

`GitService` holds a `ReentrantLock` per repository: `index.lock` races are per-repo, and a slow fetch in one
project must not block another.

### No git hooks, ever

Never propose, add, or rely on any git hook anywhere. Enforce invariants in code and prompts.

## One session, many repositories

What multiplies is **worktrees**, never agents. A task holds a list (`task/TaskRepo`, `repos.get(0)` = where
the session runs) and every per-repo step iterates it: creation cuts a worktree each
(`TaskProvisioning.resolveRepos` validates **all** of them before cutting **any**, and a failure part way
unwinds the ones already cut), `ship` asks the agent for a commit, a push and a request per repository against that
repository's own base branch, `done` deletes every worktree — the siblings hold checkouts and copied secrets nothing else
would remove.

A task's **own** repositories are one scope, not several: `StateService.findByWorktree` answers from any of
them, so a multi-repo task stays one caller however many worktrees it holds. Narrowing that back to the primary
worktree silently breaks every tool the agent calls from a sibling repo.

Three rules that are not obvious from the loop:

**The review round is merged**, and it answers as the least finished repository
(`ReviewSweepService.merged`): approved only when all are, the pipeline the single **worst** one — never a
concatenation, which reads as "success" to the caller's own check — and each comment prefixed with the
repository it came from. Reading only the session's request would let a green half advance the whole task.

**`deploy` lands in order and stops at the first conflict**, and the sentence names both sides — what is live
on the deploy branch and what is not. A shared branch cannot be written atomically whatever jagt does, so the
honest half-state beats a dry run that only makes the same failure rarer at twice the merges.

- Every repository is checked deployable before the **first** push.
- The half-state is read from **where the sequence stopped**, never from the recorded merge commits — those
  outlive the round that made them, so after a second ship every repository would read as live.
- Sibling repositories derive the same deploy worktree path (`<taskId>-deploy`, next to the repository), so the
  directory alone never decides anything: `GitService.hasDeployWorktree` asks git who cut it,
  `mergeIntoAndPush` **refuses** to finish a worktree another repository owns (it would push that repository's
  work to this one's remote), and only a task handed back at DEPLOY_CONFLICT resumes at one.
- **A directory that is no checkout is not an obstacle and never a sibling's conflict** (2026-08-21, and it
  cost a whole morning of `deploy` refusing the same task): `ide` on a deploy worktree leaves the editor
  holding it, so after the worktree is removed the editor writes its project files back into the empty
  directory. That residue is **deleted** and the deploy goes on (`clearEditorResidue`); a path holding anything
  else is left untouched and named (`StaleDeployPathException`).
- The blocked sentence follows the same rule: **a repeat is advised only when something landed**, because that
  is the case where the sibling holding the path has just released it. With nothing landed the human gets the
  obstacle instead of an instruction that loops.
- **Nothing to deploy is not a failure** (`GitService.NothingToDeployException`): a repository whose branch adds
  nothing — never touched by the change, or already on the branch — is passed over and named, which is also
  what makes starting the sequence over harmless when no worktree answers.
- A stop for any **other** reason leaves the status alone (there is nothing to resolve in a worktree) but still
  names what landed.

`revert` walks back the other way: reverse order, only the repositories that have a merge commit, each one
**forgetting** it as it comes out, so a repeat touches only what is still live — and REVERTED is set only when
everything that landed is out.

Both half-states are **stamped on the task**, not just thrown: a sentence in a console nobody scrolled back to
is not a record of a shared branch holding half a change.
