## MODIFIED Requirements

### Requirement: Plan first is a gate jagt holds
A task opened or reopened in `plan` mode, or with `agent.autoplan.enabled` on, SHALL hold its plan gate closed (`TaskState.planGate`):
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

#### Scenario: A task reopened in plan mode
- **WHEN** a task whose gate is open is reopened with `mode=plan`
- **THEN** its gate is closed until `approve-plan`, and the answer says the plan is approved on the board
