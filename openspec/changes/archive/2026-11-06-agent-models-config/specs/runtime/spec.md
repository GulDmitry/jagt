## MODIFIED Requirements

### Requirement: A fresh start reads the notes
A fresh start SHALL read `task_notes.md`, rewritten every hand-back and capped (`TaskNotes`). It SHALL run the model
its `next-model: <name>` line names, else `orchestrator.agent.model`, else the CLI default. A name outside
the allowed models SHALL refuse the hand-back as `validation`, or startup. The allowed models are `orchestrator.agent.models`
when it names any, else `AgentRuntime.models()`; where both are empty the runtime cannot choose and both are ignored.

#### Scenario: The notes or the config name a model
- **WHEN** a hand-back follows `next-model: gpt` - **THEN** it is refused as `validation`, naming the allowed models
- **WHEN** `agent.model` is `gpt` - **THEN** startup is refused, naming `gpt` and the allowed models
- **WHEN** a fresh start finds no `next-model:` line and `agent.model` is `sonnet` - **THEN** the session launches with `--model sonnet`
- **WHEN** the notes say `next-model: opus` and `agent.model` is `sonnet` - **THEN** the session launches with `--model opus`
- **WHEN** `agent.models` is `[gpt-5]` and `agent.model` is `gpt-5` - **THEN** startup passes, launching with `-m 'gpt-5'`
- **WHEN** `agent.models` is `[gpt-5]` and `agent.model` is `opus` - **THEN** startup is refused; empty keeps the runtime's list
