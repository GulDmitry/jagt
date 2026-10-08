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
`--force` or `-u`; no `commit --amend` or `reset --hard` onto a pushed commit (sub-agent rule 6). The ONE exception:
the resume rebase's `--force-with-lease` of the task's own branch. `detachUpstream` unsets the inherited
`origin/<baseBranch>` at creation. Each git call locks its repository (shared checkout). A task named after a base or
deploy branch MUST be refused.

#### Scenario: Release request
- **WHEN** resuming a `dev` → `main` request
- **THEN** refused

### Requirement: No verdict gates deploy
`FlowRules`' `DEPLOY` SHALL ask only for an open request, plus DEPLOY_CONFLICT; NEW, PLAN_PENDING, IN_PROGRESS,
VERIFYING, SHIPPING, REVERTED and DONE are refused; `ship` still runs from DEPLOYED. The confirm's `project → branch`
line comes from `TaskView.confirmations` (`TaskAction.confirmation`).

#### Scenario: Confirms
- **WHEN** you press Deploy
- **THEN** one `project → branch` line per repository, nothing else
- **AND** Revert: the branches it pushes to, the last deploy only

#### Scenario: REVERTED
- **WHEN** a task is REVERTED: `focus`, then `ship` or `done`
- **THEN** no `deploy`: re-merging it brings nothing; the agent's reports move nothing

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge wherever a `deployCommit` is recorded, DEPLOYED and DEPLOY_CONFLICT
always; it refuses with a by-hand recipe on commit absent, already reverted, conflict, or none recorded — where a
DEPLOY_CONFLICT only discards its half-merge. It walks back the merged repositories, each **forgetting** its commit, and
only then discards that half-merge; REVERTED once all that landed is out, both half-states **stamped on the task**.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`

### Requirement: Deploy stops at the first conflict
`deploy` SHALL check every repository deployable before the **first** push, land them in the task's order, and stop at
the first conflict: DEPLOY_CONFLICT, naming both sides from there.

#### Scenario: Conflict
- **WHEN** a conflict: resolve it there (`git add`), then `deploy`
- **THEN** DEPLOY_CONFLICT until you do

#### Scenario: Master acts
- **WHEN** a conflict where the Master acts
- **THEN** the session resolves it there; once staged, jagt finishes the deploy

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
on the target). Editor residue is **deleted** (`clearEditorResidue`); anything else left and named
(`StaleDeployPathException`).

#### Scenario: Blocked path
- **WHEN** `deploy` keeps refusing over the deploy directory
- **THEN** names what is in the way; a sibling's worktree by name

### Requirement: Commits carry task work, approved once
A commit SHALL carry task work, never jagt's plumbing (`WorktreeFiles.generated`, out of every diff jagt
reads); a modified `AGENTS.md` is the agent's. A ship
approves ONE commit: only the next relay replaces `task_context.md` (`writeTaskContext` truncates, `relayIfChanged`
skips an identical brief); re-reading it is no permission.

#### Scenario: Follow-up
- **WHEN** one more change after a ship: instruct the session
- **THEN** back uncommitted for review; only a new `ship` lands it

### Requirement: Ship hands the work to the agent
`ship` SHALL send ONE `ShipService` instruction for every repository holding work (changed in its worktree or ahead of
its target): commit, push, open or update a request against its own target, every link in one `update_agent_status`
(`reviewRequests`). jagt runs no git, calls no host; the task waits in SHIPPING. Nothing to ship
or deploy is no failure (`NothingToDeployException`, `holdsWork`): passed over and named, no request opened.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its own base

### Requirement: Resume reconciles origin and target
`rebaseOntoTarget` SHALL put it on what origin holds: fast-forwarded when behind, REFUSED when both sides carry commits,
left alone when only this machine's do. Then it replays on its target, pushed back under a lease, a refusal
undoing it; a CONFLICTING rebase stands in the worktree for the session. One this machine never had comes from
`origin/<branch>`, not the request's target.

#### Scenario: Base checkout
- **WHEN** the base repository holds the branch
- **THEN** `freeCheckout` detaches it **in place**, never before the strategy switch, ignoring **untracked** files
- **AND** tracked changes or another worktree holding it stay refusals

### Requirement: Push guard lives in the worktree
`.jagt/hooks/pre-push` (`WorktreeHooks`, the refs) SHALL refuse a push not to the task's branch. `WorktreeHooks.gitEnv`
sets `core.hooksPath` via `GIT_CONFIG_*` on the launch command: that session and children, no repository config. A
guardrail, not a boundary: a push built at runtime, a configured alias or a script passes; `deploy`, `revert` and a
human's shell run ungated.

#### Scenario: Hooks
- **WHEN** a client-side hook fires
- **THEN** a stub runs the repository's own, re-resolved at run time with the override off, guard first

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch or deleting the
task's, and `HEAD` where the line may move it. So is any line that could skip the hook.

#### Scenario: Skipping the hook
- **WHEN** a push line carries `--no-verify`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c` or `eval`
- **THEN** refused

### Requirement: Repositories multiply worktrees, not agents
`task/TaskRepo` SHALL be a list, `repos.get(0)` running the session. Creation is all-or-nothing (`resolveRepos`),
unwinding what was cut; a task's repositories are one scope (`findByWorktree`). A round is merged as the least finished
(`ReviewSweepService.merged`): approved only when all are, the **worst** pipeline, each comment prefixed with its
repository.

#### Scenario: Independent
- **WHEN** two repositories move independently
- **THEN** two tasks: every verb is per task

#### Scenario: Joint change
- **WHEN** `do ABC-42 api,web`
- **THEN** one task, one session, a worktree per repository

#### Scenario: Card
- **WHEN** a task spans repositories
- **THEN** each project's name links its request; one age per task

### Requirement: Done retires checkouts, never in bulk
`done` SHALL end the agent and delete every worktree and checkout the task cut; the branch survives. Bulk cleanup
(`prune all`) MUST be refused by name.

#### Scenario: Diff worktrees
- **WHEN** board-diff `jagt-diff-*` worktrees sit in the temp directory: `done <task>`
- **THEN** removed
