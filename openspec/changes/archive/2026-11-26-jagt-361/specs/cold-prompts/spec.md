## ADDED Requirements

### Requirement: The human's own cold prompt never waits
A prompt the human types into a session cold past the lifetime SHALL start its fresh session at once: their unsent
input and the turn that prompt opened are no holds; an unanswered question of the old session is. The hand-over line
SHALL be shown only where a fresh session started, as `AgentSessions.relayUnderCold` reports.

#### Scenario: A human prompt starts a fresh session at once
- **WHEN** the human types a prompt into a session cold past the lifetime
- **THEN** a fresh session starts at once and the hand-over line is shown

#### Scenario: The fresh start does not happen
- **WHEN** the fresh start does not happen
- **THEN** the prompt reaches the old session, no hand-over line is shown and a warning with its cause is logged
