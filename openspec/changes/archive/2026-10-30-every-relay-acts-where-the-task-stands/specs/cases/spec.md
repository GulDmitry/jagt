## ADDED Requirements

### Requirement: A relay acts only where the task still stands
Every relay SHALL carry the standing of the task it decided on (`flow/Standing`: lifecycle status, the way its stop ends,
the turn it holds) and SHALL refuse, opening no turn and writing nothing, where the task stands elsewhere by the time it is sent.
Writes that move none of these (usage, polls, silence, probes) never refuse one.

#### Scenario: The turn moved on since the read
- **WHEN** ABC-42's turn was opened or closed after a ship, a verification, a deploy check, a wake or a round was decided on it
- **THEN** that relay is dropped and logged with both turns, whatever step it carries
- **AND** the session is told nothing and no turn is opened

#### Scenario: Only usage was written since the read
- **WHEN** ABC-42's token usage was written after its Master verdict was read
- **THEN** the verdict is relayed as read
