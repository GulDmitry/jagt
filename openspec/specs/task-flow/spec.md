# task-flow Specification

## Purpose

A task's life: which status allows which action and where each outcome leads; how it starts is [task-start](../task-start/spec.md)'s.

## Requirements

### Requirement: One table owns the life of a task
`flow/TaskChart` SHALL hold, as one compiler-checked statechart, which statuses allow which action and what each outcome leads to;
`flow/FlowRules` answers from it. Its guard MUST
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
in for where `roles.master.mode` is `act` ([master](../master/spec.md)).

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

- **WHEN** a hand-back is `VERIFYING` and the Master runs
- **THEN** you and the session hear: verification first, then the Master

### Requirement: In act a deploy conflict is the session's
`DeployConflictJob` SHALL hand the session the conflict's worktree once per press, its own in its brief and its CLI (`AgentRuntime.admit`);
staged in full it MUST finish the press, `deploy` or `revert` ([0013](../../../docs/decisions/0013-a-deploy-conflict-is-the-sessions-in-act.md)).

#### Scenario: A conflict where the Master acts
- **WHEN** the session stages its resolution in full
- **THEN** jagt finishes the deploy; ABC-42 stopped meanwhile: nothing is pressed, the log says so once (restarts too), the lift's tick finishes it

### Requirement: A stalled deploy conflict is the human's
A handed conflict unchanged for `conflictStallMinutes` SHALL tell the human once. Its card SHALL name the paths unmerged, where and since when,
across a restart (its `Conflict`, [0063](../../../docs/decisions/0063-a-stalled-deploy-conflict-is-the-humans.md)); a status past it clears it.

#### Scenario: A conflict nobody works
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes`
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps `REVERTED`, and `DEPLOY_CONFLICT` until deployed: refusing errors every
call of that session. Its card SHALL keep the status's line (`FlowRules.holdsItsLine`), the report's logged; a question alone reaches it, until the
next report.

#### Scenario: A report at a held status
- **WHEN** a `DEPLOY_CONFLICT` task's agent reports `no changes`
- **THEN** the task stays and its card keeps the conflict, the report's line logged

### Requirement: A round read moves only a task waiting on the host or red
A round read MUST move only a task waiting on the host or red (`FlowRules.readLands`). It leaves every other status in place, its checks
alone updated: shipped work never goes back to approval, a session's work is never overwritten. It MUST leave a `QUESTION` or `PERMISSION` standing
(`FlowRules.standsThroughARead`): only the session's next report or a human answers it.

#### Scenario: A round read at another status
- **WHEN** a poll reads `APPROVED` past `DEPLOYED`
- **THEN** the task stays there
- **WHEN** a poll reads ABC-42 green
- **THEN** its `QUESTION` keeps asking, the question on the card
- **WHEN** a poll reads ABC-42 approved at `IN_PROGRESS`
- **THEN** it stays there, its checks and approval recorded
- **WHEN** a poll reads a re-run `CI_FAILED` task green with nothing unresolved
- **THEN** it moves on as a `CI_POLLING` one, no session needed

### Requirement: A follow-up request after a merged one
A task needing more work after its request merged SHALL go back to `IN_PROGRESS`; its next ship's request replaces the merged one.

#### Scenario: A follow-up request after a merged one
- **WHEN** ABC-42's request !1 merged, the task is `DEPLOYED` or `REVIEWED`, and more work is needed
- **THEN** the task is `IN_PROGRESS`; its next ship opens !2 from the same branch and target, a stop counted on !1 lifts, auto-review reads !2

### Requirement: Nothing below flow decides a status
A capability SHALL report `OK` / `RELAYED` / `CONFLICT` / `PARTIAL` / `GONE` plus the sentence and the stamp, so work serves several statuses. Its
sentence MAY name the status; nothing parses it. `withStatus` MUST appear only in `flow/` and its implementing record.
`PARTIAL` SHALL be the one refusing outcome, stamped on the task first and thrown second: half a change is recorded.

#### Scenario: Half a change lands
- **WHEN** a capability returns `PARTIAL`
- **THEN** the task is stamped, then it throws
