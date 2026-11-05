# task-start Specification

## Purpose

How a task starts: its branch and worktree, the plan gate, and what a start never loses. What follows is
[task-flow](../task-flow/spec.md)'s.

## Requirements

### Requirement: Starting a task
`do` SHALL cut a branch from the project's base into its own worktree, an agent in it; at most 24 open (`NewTaskWorktrees.MAX_TASKS`).

#### Scenario: A task starts
- **WHEN** you run `do ABC-42`, or `do ABC-42 <project>`
- **THEN** branch `ABC-42` is cut from the project's base
- **WHEN** you run `do <project> <what to do…>`
- **THEN** your words are the instructions and name the branch
- **WHEN** you run `do ABC-42 from <branch>`
- **THEN** it is cut from that branch and the request targets it ([git](../git/spec.md))

#### Scenario: Plan before code
- **WHEN** you pick `plan first`
- **THEN** the card reads `plan waiting`; `approve-plan` starts the session on it

#### Scenario: A twenty-fifth task
- **WHEN** 24 tasks are open and you start another
- **THEN** it is refused before anything is cut

### Requirement: Plan first is a gate jagt holds
A task opened or reopened in `plan` mode, or with `agent.autoplan.enabled` on, SHALL hold its plan gate closed (`TaskState.planGate`): a session's
write to any file but `plan.md`, `task_visual.md` and `task_notes.md` is refused (`PlanFirst`). `approve-plan` at `PLAN_PENDING`, and the Master's
`go` on a gate autoplan closed, SHALL open it and start the session.

#### Scenario: A source file before approval
- **WHEN** a session in `plan` mode writes `src/Foo.java` before its plan is approved
- **THEN** the write is refused with `plan first`, and `plan.md` and `task_visual.md` are still written

#### Scenario: A plan lists its files
- **WHEN** a session plans first
- **THEN** `plan.md` lists every file it will create after approval: spec delta, code, tests
- **WHEN** a session writes a file through the shell before approval
- **THEN** it is not refused ([guards](../../../docs/rules/guards.md))

#### Scenario: Approved
- **WHEN** you press `approve-plan`, or the Master in `act` finds a plan holds
- **THEN** the gate opens and the task is `IN_PROGRESS`; a `plan`-mode plan the Master found holding waits for you
- **WHEN** an autoplan session reports `IN_PROGRESS` with `outcome=no_plan`
- **THEN** the gate opens, and in `plan` mode the report is refused

#### Scenario: A ratified probe is replaced
- **WHEN** a session at `IN_PROGRESS` reports `PLAN_PENDING` with a new probes block
- **THEN** the probes are read anew and the plan waits for approval
- **AND** from `VERIFYING` the report is refused, naming `IN_PROGRESS` first

#### Scenario: A task reopened in plan mode
- **WHEN** a task whose gate is open is reopened with `mode=plan`
- **THEN** its gate is closed until `approve-plan`, and the answer says the plan is approved on the board
- **WHEN** a task at `VERIFYING`, `REVIEW_PENDING` or later is reopened with `mode=plan`
- **THEN** it is refused as a state refusal naming the status; nothing opens and its gate stays as it was

### Requirement: A task may plan before it has a repository
The board form's `plan first` with no project ticked SHALL start a task with no project, never routed (`ProjectRouting` is not asked).
A typed `do <ticket>` without a project and tracker intake SHALL still go through the router.
A start with no project and no `plan first` SHALL be refused by the server, and nothing is created.
Its planning session SHALL run in `scratch/<slug>`, a sibling of `state.json` under jagt's work dir: no git
(`GIT_CEILING_DIRECTORIES` set for that session, `scratch/` gitignored), no push, no ship; the plan gate holds there as anywhere.
`done` on such a task SHALL delete its scratch folder.

#### Scenario: Planning with no project
- **WHEN** you press Start with `plan first` ticked, no project ticked, and notes
- **THEN** the task is `PLAN_PENDING`-bound, its session runs in `scratch/<slug>` and no repository is cut

#### Scenario: A start the server refuses
- **WHEN** a start names no project and is not `plan first`
- **THEN** it is refused and nothing is created

#### Scenario: Routing stays for typed and tracker starts
- **WHEN** you type `do ABC-42` with no project, or intake opens ABC-42
- **THEN** the project is chosen by routing as before

#### Scenario: Done
- **WHEN** you `done` a task that never left its scratch folder
- **THEN** the folder is deleted

### Requirement: A scratch folder is no repository
A task with no repository SHALL hold no worktree, branch or request: its folder under `scratch/` is outside git for its session
and `ship` and `deploy` are not offered. Its repositories begin with `approve-plan`.

#### Scenario: A plan-only task
- **WHEN** a task holds only its scratch folder
- **THEN** a push, `ship` and `deploy` are unavailable and `scratch/` is untracked

### Requirement: A task with no repository plans its repositories
The brief of a task with no repository SHALL tell the session it plans in a scratch folder.
It SHALL say the plan's `## Repositories` names every project the task changes, configured or not.

#### Scenario: A plan-first session with no project
- **WHEN** such a task's session is briefed
- **THEN** it reads that `plan.md` needs `## Repositories` with a `- [changed] <key>` per project

### Requirement: An approved plan places the task
`approve-plan` on a task with no repository SHALL cut a worktree per `[changed]` key of the plan's `## Repositories`, the first primary.
It SHALL copy `plan.md` and the task's other artifacts into it.
An existing ticket branch follows the intake rule: its own commits are resumed, else it is recreated.
The scratch folder SHALL be deleted after the state write; the session starts fresh in the primary and the task is `IN_PROGRESS`.
A cut that fails SHALL unwind every cut and the task keeps its scratch folder.
A `[changed]` key `jagt.yml` lacks, or a typed base branch a named project lacks, SHALL cut nothing:
the task stays `PLAN_PENDING` and the human's question names the keys and points at the add-project form.
The Master in `act` never adds a project; its approval yields the same question.

#### Scenario: Placed
- **WHEN** you approve a plan listing `- [changed] api` and `- [changed] web`, both configured
- **THEN** worktrees exist for both, `api` runs the session fresh with the plan copied in, the scratch folder is gone, the task is `IN_PROGRESS`

#### Scenario: A key jagt.yml lacks
- **WHEN** the plan lists `- [changed] billing`, not configured
- **THEN** nothing is cut, the task stays `PLAN_PENDING` and the question names `billing` and the add-project form

#### Scenario: The Master approves
- **WHEN** the Master in `act` approves that plan
- **THEN** no project is added and the same question stands

#### Scenario: A cut fails
- **WHEN** the second worktree's cut fails
- **THEN** the first is removed and the task keeps its scratch folder

### Requirement: An existing branch is never lost
Nothing SHALL be freed or moved until `recreate` or `resume` is picked for a branch that exists.

#### Scenario: Intake meets a leftover branch
- **WHEN** intake finds the branch
- **THEN** it resumes if the branch or its origin copy holds commits the base lacks, else recreates; neither loses work

### Requirement: The base repository gives up the branch safely
A branch checked out in the base repository SHALL be freed and detached in place, a warning naming it. Uncommitted tracked changes there, or another
worktree holding it, MUST refuse naming the directory; untracked files block nothing.

#### Scenario: Tracked changes in the way
- **WHEN** uncommitted tracked changes or another worktree block the start
- **THEN** commit, stash, or free it

### Requirement: A worktree carries what the project needs
`worktree.copyGlobs` SHALL copy gitignored files (`.env`, key, cert) into every new worktree at the same path. A repository's own `CLAUDE.md`,
`AGENTS.md`, Codex config and git hooks MUST stay untouched and in force, jagt's briefing and guard beside them.

#### Scenario: A gitignored key
- **WHEN** `worktree.copyGlobs` names `.env`
- **THEN** every new worktree has it

### Requirement: A request belongs to the repository its address names
A request URL SHALL belong to the project whose remote path is the URL's leading path segments, whole segments only.
The name the remote reaches the host by is no part of it. Where several projects match, the one whose remote is on the URL's host wins.
A project with no remote is passed over.

#### Scenario: A path that only starts the same
- **WHEN** `group/api` and `group/api-shop` are two projects and the request is `https://host.test/group/api-shop/-/merge_requests/4`
- **THEN** it belongs to `group/api-shop`

#### Scenario: A remote reached by an alias
- **WHEN** a project's remote is `git@github-work:group/api.git` and the request is `https://github.com/group/api/pull/4`
- **THEN** it belongs to that project

### Requirement: A project names the remote its branches are shared through
`projects.<key>.remote` SHALL name the git remote every fetch, push and remote-tracking ref of that repository uses, `origin` where unset.
`none` SHALL mean a repository kept on this machine: worktrees are cut from the local base branch, nothing is fetched or pushed, and a
deploy merges into the local deploy branch. Startup MUST refuse a remote `git remote` does not list, and `none` beside a `codeHost`
that is not `none`, each naming its keys; it reads no network.
A base branch is read with that remote's prefix dropped, and the briefs and refusals that name a push name that remote.

#### Scenario: A repository kept on this machine
- **WHEN** `remote` is `none` and ABC-42 starts
- **THEN** its worktree is cut from the local `main` and no fetch runs

#### Scenario: A remote the repository lacks
- **WHEN** `remote` is `upstream` and `git remote` lists `origin` alone
- **THEN** startup refuses, naming `orchestrator.projects.<key>.remote`
