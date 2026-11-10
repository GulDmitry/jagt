## ADDED Requirements

### Requirement: Keys reach a session by name
The Session tab SHALL carry a row of keys: Esc, the four arrows, Tab, Space, Enter, 1 to 9, Ctrl-C, Ctrl-U. A press (`POST /api/tasks/key`) names one
`SessionHost.Key`, a fixed set the server validates; the host presses it as a key, never as typed text. The row is no verb.
#### Scenario: A menu is answered
- **WHEN** the human presses Down, then Enter, on a running session's Session tab
- **THEN** each key reaches the session's window once, in that order

#### Scenario: A name outside the set
- **WHEN** a request names a key that is not in the set
- **THEN** it is refused and nothing reaches the session

#### Scenario: No agent runs
- **WHEN** a key is pressed for a task whose window holds no running agent
- **THEN** a sentence says so and nothing is sent
