## ADDED Requirements

### Requirement: Plan first is a gate jagt holds
A task opened in `plan` mode, or with `agent.autoplan.enabled` on, SHALL hold its plan gate closed (`TaskState.planGate`):
a session's write to any file but `plan.md`, `task_visual.md` and `task_notes.md` is refused (`PlanFirst`).
`approve-plan` at `PLAN_PENDING` and the Master's `go` SHALL open it and tell the session to start; a NOT READY SHALL NOT.

#### Scenario: A source file before approval
- **WHEN** a session in `plan` mode writes `src/Foo.java` before its plan is approved
- **THEN** the write is refused with `plan first`, and `plan.md` and `task_visual.md` are still written

#### Scenario: A plan lists its files
- **WHEN** a session plans first
- **THEN** `plan.md` lists every file it will create after approval: spec delta, code, tests

#### Scenario: A write through the shell
- **WHEN** a session writes a file through the shell before approval
- **THEN** it is not refused ([guards](../../../docs/rules/guards.md))

#### Scenario: Approved on the board
- **WHEN** you press `approve-plan` on a task at `PLAN_PENDING`
- **THEN** the gate opens and the session is told the plan holds and the task is `IN_PROGRESS`

#### Scenario: A plan the Master sends back
- **WHEN** the Master reads a plan NOT READY
- **THEN** the gate stays closed

#### Scenario: A task that needs no plan
- **WHEN** an autoplan session reports `IN_PROGRESS` with `outcome=no_plan`
- **THEN** the gate opens, and in `plan` mode the report is refused

## MODIFIED Requirements

### Requirement: Starting a task
`do` SHALL cut a branch from the project's base into its own worktree, an agent in it. At most 24 tasks SHALL be
open (`NewTaskWorktrees.MAX_TASKS`).

#### Scenario: New ticket
- **WHEN** you run `do ABC-42`, or `do ABC-42 <project>`
- **THEN** branch `ABC-42` is cut from the project's base

#### Scenario: Work with no ticket
- **WHEN** you run `do <project> <what to do…>`
- **THEN** your words are the instructions and name the branch

#### Scenario: Plan before code
- **WHEN** you pick `plan first`
- **THEN** the card reads `plan waiting`; `approve-plan` starts the session on it

#### Scenario: Starting from another branch
- **WHEN** you run `do ABC-42 from <branch>`
- **THEN** it is cut from that branch and the request targets it ([git](../git/spec.md))

#### Scenario: A twenty-fifth task
- **WHEN** 24 tasks are open and you start another
- **THEN** it is refused before anything is cut
