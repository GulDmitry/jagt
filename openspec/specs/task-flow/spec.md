# task-flow Specification

## Purpose

A task's life: which status allows which action, where each outcome leads, and how work starts.

## Requirements

### Requirement: One table owns the life of a task
`flow/FlowRules` SHALL hold, in one compiler-checked Java file, which statuses allow which action and what each outcome leads to. Its guard MUST
read `flow/Facts`: an open request, a request found merged, a live deploy, and a liveness probe the projection passes as "no" (a spawn per row).

#### Scenario: An action is asked
- **WHEN** an action is asked
- **THEN** `FlowRules` allows or refuses it: `ship` is refused once git found every request of ABC-42 merged, !571 included

### Requirement: Fourteen statuses
A task SHALL be: `NEW` nothing reported; `PLAN_PENDING` a plan waits for you; `IN_PROGRESS` the agent works; `VERIFYING` jagt runs the project's
command; `REVIEW_PENDING` back with the human; `SHIPPING` a push in flight; `CI_POLLING` a round open; `CI_FAILED` checks red; `REVIEWED` nothing
unresolved, CI green, unapproved; `APPROVED` by a human; `DEPLOY_CONFLICT` resolved in the deploy worktree; `DEPLOYED` live on the deploy branch;
`REVERTED` deploy out, branch and commits kept; `DONE` closed.

#### Scenario: Nothing reported
- **WHEN** the agent has reported nothing
- **THEN** the task is `NEW`

### Requirement: A verb a human keeps is named in the table
`TaskAction.humanOnly` SHALL mark it, never prose; `done` alone is. A person opens a task and closes it; the work between is what the Master stands
in for where `master.mode` is `act` ([master](../master/spec.md)).

#### Scenario: Closing a task
- **WHEN** `done` runs
- **THEN** a human pressed it

### Requirement: Door one runs an action
`flow/FlowEngine.run` SHALL check the rules, run the action's `flow/TaskCapability`, and write the status the table gives for its `flow/Outcome`.

#### Scenario: A verb runs
- **WHEN** an allowed action finishes
- **THEN** its outcome sets the status

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status a session reports over MCP, refused unless `FlowRules.refusedReport` allows it. `REVIEWED` and `APPROVED`
MUST land only through `FlowReports.read`, the host's round read. The refusal MUST own the reason the agent acts on: no task talks itself onto a
shared branch, out of one, closed, or past its review.

#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason
- **AND** a session reporting `APPROVED` is refused: only the round read lands it

### Requirement: A hand-back waits at VERIFYING
`FlowRules.reported` SHALL redirect a hand-back owing a verification run to `VERIFYING`; no action leads there and no agent may report it.
`VerifyJob` runs the command: green reaches the human, red MUST go back to the session with the output. `FlowRules.awaitingVerification` keeps the
watchdog off that silence.

#### Scenario: A verification run
- **WHEN** `verifyCommand` and the probes run on a task at `VERIFYING`
- **THEN** green lands `REVIEW_PENDING` and runs no more; red goes to the session, not you

#### Scenario: Verification before the Master
- **WHEN** a hand-back is `VERIFYING` and the Master runs
- **THEN** you and the session hear: verification first, then the Master

### Requirement: Every deploy is checked where it landed
`DeployCheckJob` SHALL ask the session once per deploy commit, keyed on the task's turn, so neither another relay nor a restart asks again; what it
asks is the brief's `deploy` step. Green MUST report nothing, the task staying `DEPLOYED`; red is the session's `IN_PROGRESS`, the round running
again: reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`

### Requirement: In act a deploy conflict is the session's
`DeployConflictJob` SHALL hand the session the deploy worktree once per deploy press, its own in its brief and its CLI (`AgentRuntime.admit`);
staged in full it MUST finish the press ([0013](../../../docs/decisions/0013-a-deploy-conflict-is-the-sessions-in-act.md)).

#### Scenario: A conflict where the Master acts
- **WHEN** the session stages its resolution in full
- **THEN** jagt finishes the deploy; ABC-42 stopped meanwhile, nothing is pressed and the log says so once, the tick after the lift finishes it

### Requirement: A stalled deploy conflict is the human's
A handed conflict unchanged for `conflictStallMinutes` SHALL tell the human once. Its card SHALL name the paths unmerged, where and since when,
across a restart (`TaskState.conflict`, [0063](../../../docs/decisions/0063-a-stalled-deploy-conflict-is-the-humans.md)); a status past it clears it.

#### Scenario: A conflict nobody works
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes`
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps `REVERTED`, and `DEPLOY_CONFLICT` until deployed: refusing errors every
call of that session. Its card SHALL keep the status's line (`FlowRules.holdsItsLine`), the report's logged; a question alone reaches it, until the
next report. A round read MUST leave a `DEPLOYED`, `DEPLOY_CONFLICT` or `DONE` task in place: shipped work never goes back to approval.

#### Scenario: A report at a held status
- **WHEN** a `DEPLOY_CONFLICT` task's agent reports `no changes`
- **THEN** the task stays and its card keeps the conflict, the report's line logged
- **AND** a poll reading `APPROVED` past `DEPLOYED` leaves the task there

### Requirement: A follow-up request after a merged one
A task needing more work after its request merged SHALL go back to `IN_PROGRESS`; its next ship's request replaces the merged one.

#### Scenario: A follow-up request after a merged one
- **WHEN** ABC-42's request !1 merged, the task is `DEPLOYED` or `REVIEWED`, and more work is needed
- **THEN** the task is `IN_PROGRESS`; its next ship opens !2 from the same branch and target, a stop counted on !1 lifts, auto-review reads !2

### Requirement: Nothing below flow decides a status
A capability SHALL report `OK` / `RELAYED` / `CONFLICT` / `PARTIAL` / `GONE` plus the sentence and the stamp, so work serves several statuses. Its
sentence MAY name the status; nothing parses it. `withStatus` MUST appear only in `flow/` and its implementing record.

#### Scenario: Grepping for withStatus
- **WHEN** the sources are grepped
- **THEN** only `flow/` and its record match

### Requirement: PARTIAL refuses
`PARTIAL` SHALL be the one refusing outcome, stamped on the task first and thrown second: half a change is recorded.

#### Scenario: Half a change lands
- **WHEN** a capability returns `PARTIAL`
- **THEN** the task is stamped, then it throws

### Requirement: Starting a task
`do` SHALL cut a branch from the project's base into its own worktree, an agent in it; at most 24 open (`NewTaskWorktrees.MAX_TASKS`).

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

#### Scenario: A write through the shell
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
