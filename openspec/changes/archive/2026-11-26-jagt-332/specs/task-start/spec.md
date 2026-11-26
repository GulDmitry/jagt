## MODIFIED Requirements

### Requirement: Plan first is a gate jagt holds
A task opened or reopened in `plan` mode, or with `agent.autoplan.enabled` on, SHALL hold its plan gate closed (`TaskState.planGate`): a session's
write to any file but `plan.md`, `task_visual.md` and `task_notes.md` is refused (`PlanFirst`). `approve-plan` at `PLAN_PENDING`, and the Master's
`go` on a gate autoplan closed, SHALL open it and start the session.
While it is closed the flow also refuses `ship` and a report of `REVIEW_PENDING`, `SHIPPING` or `CI_POLLING`.

#### Scenario: A source file before approval
- **WHEN** a session in `plan` mode writes `src/Foo.java` before its plan is approved
- **THEN** the write is refused with `plan first`, and `plan.md` and `task_visual.md` are still written
- **WHEN** a session on a closed gate asks a question and the Master does not hold `plan`
- **THEN** the question waits for the human and the Master writes no answer
- **WHEN** intake or a code host opens ABC-42 with autoplan off
- **THEN** its plan gate is closed and no source file is written before the plan is approved
- **AND** its session judges the ticket first: `plan.md` opens with `verdict: sound` or `verdict: unsound — <why>`
- **WHEN** the session judges ABC-42 unsound
- **THEN** it reports a question naming why; the Master in act neither answers nor approves, the plan waits for you
- **WHEN** the session judges ABC-42 sound
- **THEN** the Master in act approves the plan as for any plan-first task

#### Scenario: A plan lists its files
- **WHEN** a session plans first
- **THEN** `plan.md` lists every file it will create after approval: spec delta, code, tests
- **WHEN** a session writes a file through the shell before approval
- **THEN** the write is not refused ([guards](../../../docs/rules/guards.md)), but its `REVIEW_PENDING` report and `ship` are, naming the plan
- **WHEN** a review round is relayed to a task at `REVIEW_PENDING` whose gate is closed
- **THEN** the gate opens and the session's writes are not refused as plan first

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
