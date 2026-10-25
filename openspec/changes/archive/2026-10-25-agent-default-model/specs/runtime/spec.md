## MODIFIED Requirements

### Requirement: A fresh start reads the notes
A fresh start SHALL read `task_notes.md`, rewritten every hand-back and capped (`TaskNotes`). It SHALL run the model
its `next-model: <name>` line names, else `orchestrator.agent.model`, else the CLI default. A name outside
`AgentRuntime.models()` SHALL refuse the hand-back as `validation`, or startup where `agent.model` names it; a runtime
that cannot choose ignores both.

#### Scenario: The notes or the config name a model
- **WHEN** a hand-back follows `next-model: gpt`
- **THEN** it is refused as `validation`, naming the allowed models
- **WHEN** `agent.model` is `gpt`
- **THEN** startup is refused, naming `gpt` and the allowed models
- **WHEN** a fresh start finds no `next-model:` line and `agent.model` is `sonnet`
- **THEN** the session launches with `--model sonnet`
- **WHEN** the notes say `next-model: opus` and `agent.model` is `sonnet`
- **THEN** the session launches with `--model opus`
