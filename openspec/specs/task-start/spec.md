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
A request URL SHALL belong to the project whose remote is on the URL's host. Its path MUST be the URL's leading path segments, whole
segments only. A project with no remote is passed over.

#### Scenario: A path that only starts the same
- **WHEN** `group/api` and `group/api-shop` are two projects and the request is `https://host.test/group/api-shop/-/merge_requests/4`
- **THEN** it belongs to `group/api-shop`
