## MODIFIED Requirements

### Requirement: In act a deploy conflict is the session's
`DeployConflictJob` SHALL hand the session the deploy worktree, or a revert conflict's revert worktree, once per press, its own in its brief and its CLI (`AgentRuntime.admit`);
staged in full it MUST finish the press: `deploy` or `revert`, whichever the human pressed ([0013](../../../docs/decisions/0013-a-deploy-conflict-is-the-sessions-in-act.md)).

#### Scenario: A conflict where the Master acts
- **WHEN** the session stages its resolution in full
- **THEN** jagt finishes the deploy; ABC-42 stopped meanwhile: nothing is pressed, the log says so once (restarts too), the lift's tick finishes it

#### Scenario: A revert conflict where the Master acts
- **WHEN** the session stages a revert conflict's resolution in full
- **THEN** jagt presses `revert` under the rules it presses `deploy` by; where it may not act, the resolved conflict waits for the human
