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
A handed conflict unchanged for `conflictStallMinutes` SHALL go to the Master first where it answers:
`act`, `answer` not kept by the human, task not stopped, no question standing. The stall becomes the session's `QUESTION` at `DEPLOY_CONFLICT`,
answered by the Master as any question is
([0134](../../../docs/decisions/0134-a-stalled-deploy-conflict-asks-the-master-first.md)).
The `Conflict` SHALL record that it was asked, and the stall clock restarts then.

#### Scenario: A conflict nobody works, where the Master answers
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes` and the Master acts
- **THEN** no banner: ABC-42's report is a `QUESTION` naming the paths unmerged, its worktree and since when, and the Master answers it
- **AND** the answer is relayed to the same session, and a restart in between asks nothing again

### Requirement: A stall the Master does not settle is told to the human
A stall after the Master was asked, or one where the Master does not answer, SHALL tell the human once per stall.
A worktree moved since the last telling makes the next stall a new one. Its card SHALL name the paths unmerged,
where and since when, across a restart (its `Conflict`, [0063](../../../docs/decisions/0063-a-stalled-deploy-conflict-is-the-humans.md));
a status past it clears it.

#### Scenario: The Master's question goes unanswered or the worktree stays unchanged
- **WHEN** the worktree has not changed for `conflictStallMinutes` since the Master was asked
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when

#### Scenario: A conflict nobody works
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes` and the Master does not answer (`judge`)
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when

#### Scenario: A conflict worked after the human was told, then left again
- **WHEN** ABC-42's deploy worktree moved after the banner, then has not changed for `conflictStallMinutes`
- **THEN** one more banner, and the card says NEEDS YOU again

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

### Requirement: A closed request takes its repository out of the task
Where a task holds several repositories and the host says one's request was closed unmerged, that repository SHALL
leave the task: its checkout is removed, its branch kept, the session told in one line, and the sweep judges the others
in the same pass. A task's only repository, and a request the host says does not exist, stay gone.
The one a deploy was handed back on for its merge leaves the record and its deploy worktree is removed; the task stays
`DEPLOY_CONFLICT` while repositories after it are unpushed, and the next `deploy` lands them.

#### Scenario: One of three requests closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, and the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task, its checkout removed and its branch kept, and the session is told which repository left and why
- **AND** the round is the request of `alpha` and `gamma`; `deploy` and the card never name `beta` again

#### Scenario: The only repository closed
- **WHEN** ABC-42 holds `alpha` alone and the host says its request was closed unmerged
- **THEN** the sweep calls it gone, naming the request

#### Scenario: A request that does not exist
- **WHEN** ABC-42 spans `alpha` and `beta` and the host says `beta`'s request does not exist
- **THEN** the sweep calls it gone, and `beta` stays in the task

#### Scenario: A repository already landed
- **WHEN** `beta` was deployed, then its request reads closed
- **THEN** `beta` stays in the deployment's record, so `revert` still covers it

#### Scenario: The repository a deploy waits on closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, all landed, and `beta`'s pipeline went red, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta`, the one the deploy was handed back on, leaves the task and the deployment's record, no revert of it is owed
- **AND** the task lands `DEPLOYED` on `alpha` and `gamma`
- **AND** the stalled-conflict line ends, and the deploy check goes on for `alpha` and `gamma` as for any deploy

#### Scenario: The repository a deploy's merge conflicted on closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, `alpha` landed and `beta`'s merge conflicted, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task and the deployment's record, its deploy worktree removed
- **AND** the task stays `DEPLOY_CONFLICT`, `gamma` unpushed, until `deploy` is pressed, which lands `gamma` after `alpha`
