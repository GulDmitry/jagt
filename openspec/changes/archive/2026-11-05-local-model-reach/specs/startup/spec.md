## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: A local endpoint is read for its model
`orchestrator.opencode.endpoint` and `orchestrator.assistant.local.endpoint`, when set, SHALL each be read every minute
(`<endpoint>/models`); one not answering or not listing its `model` is jagt's lost reach on the board's head, not pressable.

#### Scenario: A local endpoint not serving its configured model is a lost reach until a read lists it
- **WHEN** ABC-42 runs and `assistant.local.model` is `qwen` while the endpoint lists only `other`
- **THEN** the head says `<endpoint> does not serve qwen`, no card stops, and a read listing `qwen` clears it
