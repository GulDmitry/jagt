## MODIFIED Requirements

### Requirement: What jagt revives it re-enters
A session jagt revives SHALL re-enter (`AgentRuntime.reviveCommand`, Claude `--continue`): a lost tmux server costs
the window, not the context. A human's `respawn` SHALL be fresh, as SHALL a review round (`AgentSessions.relayFresh`).
So SHALL a relay past the cache lifetime (`AgentRuntime.pastContinuing`), read on the session's own log as it is sent.

#### Scenario: The tmux server is lost
- **WHEN** jagt revives a session after its tmux server died
- **THEN** the revived session re-enters with `--continue`

#### Scenario: A relay comes late
- **WHEN** a relay comes after the cache lifetime
- **THEN** the session starts fresh from the current `task_notes.md`, on the model its `next-model:` line names

#### Scenario: A review round comes early, or the human respawns
- **WHEN** a review round comes within the cache lifetime, a headless run wrote after the session, or `respawn` runs
- **THEN** the session starts fresh
