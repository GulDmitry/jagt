## ADDED Requirements

### Requirement: OpenCode may run the agents
`orchestrator.agent.cli: opencode` SHALL run every session on OpenCode, reaching jagt as a remote MCP server carrying the
task's worktree as its caller. Its config SHALL be jagt's own; the project's `opencode.json` neither replaces nor feeds it.
`orchestrator.opencode.endpoint` and `model`, when set, SHALL be the sessions' model; blank, the user's own OpenCode config
chooses. Startup SHALL refuse OpenCode with `agent.sandbox.enabled`, an endpoint that is no http(s) address, and an
endpoint without a model.

#### Scenario: A local model
- **WHEN** `endpoint` and `model` are set under `orchestrator.opencode`
- **THEN** each session starts on that model

#### Scenario: A plan
- **WHEN** a task starts in plan mode
- **THEN** its session starts on OpenCode's plan agent

#### Scenario: A sandbox
- **WHEN** `agent.cli` is `opencode` and `agent.sandbox.enabled` is true
- **THEN** the start is refused, naming the key
