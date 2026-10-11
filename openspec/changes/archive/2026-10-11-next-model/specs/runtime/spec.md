## MODIFIED Requirements

### Requirement: What jagt revives it re-enters
A session jagt revives SHALL re-enter (`AgentRuntime.reviveCommand`, Claude `--continue`): a lost tmux server costs
the window, not the context. A human's `respawn` SHALL be fresh. A relay past the cache lifetime
(`AgentRuntime.continuesWithin`, Claude 1h) SHALL start fresh from `task_notes.md`: rewritten every hand-back,
capped (`TaskNotes`). A fresh start SHALL run the model its `next-model: <name>` line names, else the CLI default.
A name outside `AgentRuntime.models()` (Claude `fable`, `opus`, `sonnet`, `haiku`) refuses the hand-back as
`validation`; a runtime that cannot choose ignores it.

#### Scenario: The tmux server is lost
- **WHEN** jagt revives a session after its tmux server died
- **THEN** the revived session re-enters with `--continue`

#### Scenario: A relay comes late
- **WHEN** a relay comes after the cache lifetime
- **THEN** the session starts fresh from the current `task_notes.md`, on the model its `next-model:` line names

#### Scenario: The notes name an unknown model
- **WHEN** a hand-back follows `next-model: gpt`
- **THEN** it is refused as `validation`, naming the allowed models

#### Scenario: The human respawns
- **WHEN** the human runs `respawn`
- **THEN** the session starts fresh
