## ADDED Requirements

### Requirement: An MCP caller is a task or the Master, never assumed
A call whose `X-Working-Directory` lies in a task's worktree SHALL be that task's. Else it SHALL present the root's
`.jagt/master-token`, drawn at each start (`surface/mcp/MasterToken`), to be the Master's, or be refused 401.

#### Scenario: Nobody in particular
- **WHEN** a `curl` without the token, or a worktree no task holds, calls a tool
- **THEN** it is refused, never promoted to the Master
