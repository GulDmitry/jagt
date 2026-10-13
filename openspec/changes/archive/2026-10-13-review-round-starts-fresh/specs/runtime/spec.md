## MODIFIED Requirements

### Requirement: What jagt revives it re-enters
A session jagt revives SHALL re-enter (`AgentRuntime.reviveCommand`, Claude `--continue`): a lost tmux server costs
the window, not the context. A human's `respawn` SHALL be fresh, as SHALL a review round (`relayFreshIfChanged`).
So SHALL a relay past the cache lifetime (`AgentRuntime.continuesWithin`), read on the session's own log as it is sent.
A fresh start reads `task_notes.md`, rewritten every hand-back and capped (`TaskNotes`). It runs the model its
`next-model: <name>` line names, else the CLI default. A name outside `AgentRuntime.models()` refuses the hand-back
as `validation`; a runtime that cannot choose ignores it.

#### Scenario: The tmux server is lost
- **WHEN** jagt revives a session after its tmux server died
- **THEN** the revived session re-enters with `--continue`

#### Scenario: A relay comes late
- **WHEN** a relay comes after the cache lifetime
- **THEN** the session starts fresh from the current `task_notes.md`, on the model its `next-model:` line names

#### Scenario: A review round comes early
- **WHEN** a review round comes within the cache lifetime, or a headless run wrote after the session
- **THEN** the session starts fresh

#### Scenario: The notes name an unknown model
- **WHEN** a hand-back follows `next-model: gpt`
- **THEN** it is refused as `validation`, naming the allowed models

#### Scenario: The human respawns
- **WHEN** the human runs `respawn`
- **THEN** the session starts fresh
