## MODIFIED Requirements

### Requirement: OpenCode may run the agents
`orchestrator.agent.cli: opencode` SHALL run every session on OpenCode, reaching jagt as a remote MCP server carrying the
task's worktree as its caller. Its config SHALL be jagt's own; the project's `opencode.json` neither replaces nor feeds it.
`orchestrator.opencode.endpoint` and `model`, when set, SHALL be the sessions' model; blank, the user's own OpenCode config
chooses. Startup SHALL refuse OpenCode with `agent.sandbox.enabled`, an endpoint that is no http(s) address, and an
endpoint without a model. A set endpoint SHALL be read every minute (`<endpoint>/models`): one that does not answer is
jagt's lost reach on the board's head, not pressable, ended by the next read that answers.

#### Scenario: A local model
- **WHEN** `endpoint` and `model` are set under `orchestrator.opencode`
- **THEN** each session starts on that model

#### Scenario: A plan
- **WHEN** a task starts in plan mode
- **THEN** its session starts on OpenCode's plan agent

#### Scenario: A sandbox
- **WHEN** `agent.cli` is `opencode` and `agent.sandbox.enabled` is true
- **THEN** the start is refused, naming the key

#### Scenario: The local endpoint stops answering
- **WHEN** the endpoint's `/models` does not answer while ABC-42 runs
- **THEN** the board's head says `opencode cannot reach <endpoint>`, and no card stops
- **AND** the next read that answers clears it, with no restart
