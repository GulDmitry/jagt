## MODIFIED Requirements

### Requirement: In act a deploy conflict is the session's
`DeployConflictJob` SHALL hand the session the deploy worktree once per deploy press, its own in its brief and its CLI (`AgentRuntime.admit`);
staged in full it MUST finish the press ([0013](../../../docs/decisions/0013-a-deploy-conflict-is-the-sessions-in-act.md)).

#### Scenario: A conflict where the Master acts
- **WHEN** the session stages its resolution in full
- **THEN** jagt finishes the deploy; ABC-42 stopped meanwhile, nothing is pressed and the log says so once, the tick after the lift finishes it
