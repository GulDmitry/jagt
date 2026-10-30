## MODIFIED Requirements

### Requirement: In act a deploy conflict is the session's
`DeployConflictJob` SHALL hand the session the deploy worktree once per deploy press, its own in its brief and its CLI (`AgentRuntime.admit`);
staged in full it MUST finish the press ([0013](../../../docs/decisions/0013-a-deploy-conflict-is-the-sessions-in-act.md)).

#### Scenario: A conflict where the Master acts
- **WHEN** the session stages its resolution in full
- **THEN** jagt finishes the deploy; ABC-42 stopped meanwhile: nothing is pressed, the log says so once (restarts too), the lift's tick finishes it

### Requirement: A stalled deploy conflict is the human's
A handed conflict unchanged for `conflictStallMinutes` SHALL tell the human once. Its card SHALL name the paths unmerged, where and since when,
across a restart (its `Conflict`, [0063](../../../docs/decisions/0063-a-stalled-deploy-conflict-is-the-humans.md)); a status past it clears it.

#### Scenario: A conflict nobody works
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes`
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when
