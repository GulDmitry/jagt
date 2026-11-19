## MODIFIED Requirements

### Requirement: An MCP caller is a task or the Master, never assumed
A call whose `X-Working-Directory` lies in a task's worktree SHALL be that task's. Else it SHALL present the
`master-token` beside `state.json`, drawn at each start (`surface/mcp/refusal/MasterToken`), to be the Master's, or be refused 401.

#### Scenario: Nobody in particular
- **WHEN** a tokenless `curl`, or a worktree no task holds, calls
- **THEN** it is refused, never the Master

#### Scenario: What a sub-agent sees
- **WHEN** a sub-agent lists or calls tools
- **THEN** it has `update_agent_status`, `notify_user` and `open_code`; every other tool is the Master's alone

#### Scenario: A tool name nobody declared
- **WHEN** a caller names an unknown tool
- **THEN** the refusal lists its tools
