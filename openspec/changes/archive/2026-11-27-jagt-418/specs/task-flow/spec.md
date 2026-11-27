## ADDED Requirements

### Requirement: Every kind of move has a class in every state
`flow/MoveClasses` SHALL name the regions each kind of move concerns; each state of them MUST take it by an edge, ignore it with its why or
refuse it under a name, else building fails naming both ([0264](../../../docs/decisions/0264-a-move-has-a-declared-class-in-every-state.md)).
A fact move meeting a state with no class SHALL log `move unclassified` and mark the board's head; a claim or a press MUST be refused.
#### Scenario: A state with no class
- **WHEN** a kind of move has no edge and no declaration for a state of a region it is addressed to
- **THEN** building fails with `move Reported has no class in state lifecycle.DONE`
- **AND** a fact met in production without one is logged and marks the head, taking what edges it has

## MODIFIED Requirements

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status a session reports over MCP, refused unless `FlowRules.refusedReport` allows it. `REVIEWED` and `APPROVED`
MUST land only through `FlowReports.read`, the host's round read. The refusal MUST own the reason the agent acts on: no task talks itself onto a
shared branch, out of one, closed, past its review. An outside system's plan gate is held the same way ([task-start](../task-start/spec.md)).
#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason
- **AND** a session reporting `APPROVED` is refused: only the round read lands it
- **AND** a `DEPLOYED` task's session reporting `SHIPPING` lands: it keeps working after the deploy, and a refusal errors every call

### Requirement: A hand-back waits at VERIFYING
`FlowRules.reported` SHALL redirect a hand-back owing a verification run to `VERIFYING`; no action leads there and no agent may report it.
`VerifyJob` runs the command: green reaches the human, red MUST go back to the session with the output. `FlowRules.awaitingVerification` keeps the
watchdog off that silence.
#### Scenario: A verification run
- **WHEN** `verifyCommand` and the probes run on a task at `VERIFYING`
- **THEN** green lands `REVIEW_PENDING` and runs no more; red goes to the session, not you
- **WHEN** a hand-back is `VERIFYING` and the Master runs
- **THEN** you and the session hear: verification first, then the Master

### Requirement: A project bounds its verify command
The verify command SHALL be stopped after the project's `verifyMinutes`, twenty minutes where none is written or it is not positive.
#### Scenario: A project with a limit
- **WHEN** a project has `verifyMinutes: 5` and its verify command runs at `VERIFYING`
- **THEN** the command is stopped after 5 minutes
#### Scenario: A project with no limit
- **WHEN** a project has no `verifyMinutes`
- **THEN** its verify command is stopped after 20 minutes

### Requirement: A stall the Master does not settle is told to the human
A stall after the Master was asked, or one where the Master does not answer, SHALL tell the human once per stall. A worktree moved since the last
telling makes the next stall a new one. Its card SHALL name the paths unmerged, where and since when, across a restart (its `Conflict`,
[0063](../../../docs/decisions/0063-a-stalled-deploy-conflict-is-the-humans.md)); a status past it clears it.
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

### Requirement: A follow-up request after a merged one
A task needing more work after its request merged SHALL go back to `IN_PROGRESS`; its next ship's request replaces the merged one.
#### Scenario: A follow-up request after a merged one
- **WHEN** ABC-42's request !1 merged, the task is `DEPLOYED` or `REVIEWED`, and more work is needed
- **THEN** the task is `IN_PROGRESS`; its next ship opens !2 from the same branch and target, a stop counted on !1 lifts, auto-review reads !2
