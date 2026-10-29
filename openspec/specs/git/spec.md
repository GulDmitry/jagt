# git Specification

## Purpose

Shared-branch writes, commits, ships, worktree hooks and multi-repo tasks.

## Requirements

### Requirement: Only deploy and revert write shared branches
`deploy` SHALL merge `origin/<task>` into `deployBranch` (`mergeIntoAndPush`), `revert` its merge commit
(`revertMergeAndPush`); Master-only (`Audience.MASTER`). `do <ticket> from <branch>` (`TaskState.baseBranch`) moves
the cut and the request's **target**, never the merge destination (`baseBranchOr`). A `deployBranch` naming
`baseBranch`, however spelled, MUST be refused.

#### Scenario: Diff base
- **WHEN** `ide <task> diff` shows files the task never touched
- **THEN** read against the request's target, never `deployBranch`

### Requirement: Nothing rewrites what left the machine
`GitDeploy` (`HEAD:<target>`) and `GitWorktrees` (`refs/heads/x:refs/heads/x`) SHALL push **one** branch, never
`--force` or `-u`; no `commit --amend` or `reset --hard` onto a pushed commit (sub-agent rule 6). The one exception:
a rebase of the task's own branch onto its request's target. The resume or the session pushes it under
`--force-with-lease` when the target moved on. `detachUpstream` unsets the inherited `origin/<baseBranch>` at creation. Each
git call locks its repository (shared checkout). A task named after a base or deploy branch MUST be refused.

#### Scenario: Release request
- **WHEN** resuming a `dev` → `main` request
- **THEN** refused

#### Scenario: Target moved on
- **WHEN** the target gained commits after the branch was cut, or the session ships
- **THEN** it rebases onto the target and lease-pushes before the request is updated, so the request lists only the task's commits

### Requirement: No verdict gates deploy
`FlowRules`' `DEPLOY` SHALL ask only for an open request, plus DEPLOY_CONFLICT; NEW, PLAN_PENDING, IN_PROGRESS, VERIFYING, SHIPPING, REVERTED and DONE
are refused; `ship` still runs from DEPLOYED. The confirm's `project → branch` line comes from `TaskView.confirmations` (`TaskAction.confirmation`).

#### Scenario: Confirms
- **WHEN** you press Deploy
- **THEN** one `project → branch` line per repository, nothing else
- **AND** Revert: the branches it pushes to, the last deploy only

#### Scenario: REVERTED
- **WHEN** a task is REVERTED: `focus`, then `ship` or `done`
- **THEN** no `deploy`: re-merging it brings nothing

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge wherever a `deployCommit` is recorded, DEPLOYED and DEPLOY_CONFLICT
always; it refuses with a by-hand recipe where that is absent, reverted or conflicts. It walks back the merged
repositories, each **forgetting** its commit, then discards a DEPLOY_CONFLICT's half-merge, sought only where
`jagt.yml` still names the project; REVERTED once all that landed is out.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`

#### Scenario: Revert from a conflict with nothing landed
- **WHEN** `revert` from DEPLOY_CONFLICT, no commit recorded
- **THEN** the half-merge is discarded; REVERTED

#### Scenario: Part-way revert
- **WHEN** `revert` stops part way
- **THEN** **stamped on the task**: DEPLOYED, or from DEPLOY_CONFLICT still DEPLOY_CONFLICT, its half-merge waiting

### Requirement: Deploy stops at the first conflict
`deploy` SHALL check every repository deployable before the **first** push, land them in the task's order, and stop at
the first conflict: DEPLOY_CONFLICT, naming both sides from there.

#### Scenario: Conflict
- **WHEN** a conflict: resolve it (`git add`), then `deploy`
- **THEN** DEPLOY_CONFLICT until you do

#### Scenario: Multi-repo deploy
- **WHEN** `deploy ABC-42` and one conflicts after another landed
- **THEN** the next `deploy` resumes there

#### Scenario: Break-off
- **WHEN** it breaks off for something no worktree fixes
- **THEN** status untouched, naming what is already live

### Requirement: Siblings share one deploy path
Siblings SHALL derive one deploy path (`<taskId>-deploy`): `hasDeployWorktree` asks git who cut it,
`mergeIntoAndPush` **refuses** another repository's, only DEPLOY_CONFLICT resumes. Every press starts the merge over
unless the human's own work sits there (staged, committed or part done; committed only with its base
on the target). Editor residue is **deleted** (`clearEditorResidue`); anything else left and named (`StaleDeployPathException`).

#### Scenario: Blocked path
- **WHEN** `deploy` keeps refusing over the deploy directory
- **THEN** names what is in the way; a sibling's worktree by name

### Requirement: Commits carry task work, approved once
A commit SHALL carry task work, never jagt's plumbing (`WorktreeFiles.generated`, out of every diff jagt reads); a modified `AGENTS.md` is the
agent's. A ship approves ONE commit: only the next relay replaces `task_context.md`; re-reading it is no permission.

#### Scenario: Follow-up
- **WHEN** one more change after a ship: instruct the session
- **THEN** back uncommitted for review; only a new `ship` lands it

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`). jagt calls no host and writes no git; the task waits in SHIPPING.
Nothing to ship or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.
A request is merged when the host says so in a review read, which stands: git alone never sees a squash. Otherwise git decides: a merge commit of the
target took the branch tip in (`mergedIntoTarget`, after a fetch; a failed fetch is no answer). `MergedReadJob` asks again every 5 minutes.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base
- **AND** with !571 merged, nothing is shipped and no SHIPPING step is recorded; a squash counts by the host's word, a never-diverged branch not
- **AND** once git no longer finds !571's tip in the target and the host never said it merged, `ship` is allowed and auto-review polls again

### Requirement: A new base is reported by the session
A session that rebased onto a new base on the human's or the Master's word SHALL report it with `retarget_task`;
jagt records it (`TaskRetarget`) only once origin has the branch and every worktree's HEAD holds it.

#### Scenario: A task stacked on another
- **WHEN** ABC-42's session rebases onto `ABC-41` and calls `retarget_task` with it
- **THEN** ship targets `ABC-41`, and the diff is read against it

#### Scenario: A base the worktree does not stand on
- **WHEN** `retarget_task` names a branch origin lacks, or one HEAD does not hold
- **THEN** it is refused and the base stays

### Requirement: Resume reconciles origin and target
`rebaseOntoTarget` SHALL put it on what origin holds: fast-forwarded when behind, REFUSED when both sides carry commits, left alone
when only this machine's do. Then it replays on its target, pushed back under a lease, a refusal undoing it; a CONFLICTING rebase
stands in the worktree for the session. One this machine never had comes from `origin/<branch>`, not the request's target.

#### Scenario: Base checkout
- **WHEN** the base repository holds the branch
- **THEN** `freeCheckout` detaches it **in place**, never before the strategy switch, ignoring **untracked** files
- **AND** tracked changes or another worktree holding it stay refusals

### Requirement: Push guard lives in the worktree
`.jagt/hooks/pre-push` (`WorktreeHooks`, the refs) SHALL refuse a push not to the task's branch. `WorktreeHooks.gitEnv`
sets `core.hooksPath` via `GIT_CONFIG_*` on the launch command: that session and children, no repository config. A
guardrail: a runtime-built push, an alias or a script passes; `deploy`, `revert` and a human's shell run ungated.

#### Scenario: Hooks
- **WHEN** a client-side hook fires
- **THEN** a stub runs the repository's own, re-resolved at run time with the override off, guard first

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch, deleting the task's
(`--de`, `-ud`) or forced unleased; `HEAD` unless bare and unmoved; `send-pack`, `http-push`, `receive-pack`; a `gh`/`glab`
call but a read, however wrapped (quoted as data, it runs); a line naming the board's port or the Master's token.

#### Scenario: Skipping the hook
- **WHEN** a push line holds `--no-verify`, `git -c`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c` or
  `eval`, however quoted; a git line holds `$'…'` or `$"…"`
- **THEN** refused

### Requirement: Repositories multiply worktrees, not agents
`task/TaskRepo` SHALL be a list, `repos.get(0)` running the session. Creation is all-or-nothing (`resolveRepos`),
unwinding what was cut wherever it fails; a task's repositories are one scope (`findByWorktree`). A round is merged
as the least finished (`RoundReading.merged`): approved only when all are, the **worst** pipeline, each comment prefixed with its repository.

#### Scenario: Independent
- **WHEN** two repositories move independently
- **THEN** two tasks, every verb per task

#### Scenario: Joint change
- **WHEN** `do ABC-42 api,web`
- **THEN** one task, one session, a worktree per repository

### Requirement: Done retires checkouts, never in bulk
`done` SHALL end the agent and delete every worktree and checkout the task cut; the branch survives. `prune all` MUST be refused by name.

#### Scenario: Diff worktrees
- **WHEN** `done <task>` with `jagt-diff-*` worktrees
- **THEN** removed, its project in `jagt.yml` or gone

### Requirement: A running task gains a repository
`add-repo` SHALL cut the task branch's worktree in the project the request names, as `do` does (`NewTaskWorktrees`),
or adopt one already on that branch (`GitWorktrees.worktreeOf`). It SHALL register it after the session's own and
rewrite every worktree's briefing and sandbox. It SHALL clear the request, hand the session the path and move the task
to `IN_PROGRESS`. A cut that fails SHALL unwind and leave the request standing, the cause on the card.

#### Scenario: The request is granted
- **WHEN** you press `add-repo` on ABC-42, which asked for `schemaRegistry`
- **THEN** its worktree exists, the session is told where, the request is gone and the task is `IN_PROGRESS`
- **AND** a status only a human leaves keeps its own

#### Scenario: The cut fails
- **WHEN** git refuses the worktree
- **THEN** nothing of it is left, the request stands, and the card reads NEEDS INPUT with the cause

#### Scenario: A worktree already stands on the branch
- **WHEN** ABC-42's branch is checked out in a `schemaRegistry` worktree jagt did not cut
- **THEN** `add-repo` registers that worktree, its uncommitted work untouched, and a failure leaves it in place

#### Scenario: The session's repository ships its own memory file
- **WHEN** ABC-42's first repository ships `CLAUDE.md`, so jagt briefed it in `CLAUDE.local.md`
- **THEN** `add-repo` writes that briefing again in `CLAUDE.local.md` and attaches

#### Scenario: The attached repository is trusted as it stands
- **WHEN** `add-repo` attaches `schemaRegistry` to ABC-42
- **THEN** its git config and hooks join the trusted record as they are, and the next `ship` is not refused for them
