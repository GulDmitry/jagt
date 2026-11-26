## ADDED Requirements

### Requirement: A task's screen is read, never typed into
`read_screen` SHALL be `Audience.ROOT` and `Effect.REPORTS`; `GET /api/debug/screen` answers the same read under `/api`.
It SHALL show the last 1 to 1000 `lines` of the task's terminal, 50 where left out, colour codes removed, and send no key.
A value outside SHALL be refused naming `lines`, and nothing is read.
#### Scenario: A screen
- **WHEN** a CLI with the Master token calls `read_screen` for ABC-42
- **THEN** it receives the pane's last lines and no key reaches the session
#### Scenario: A sub-agent reads a screen
- **WHEN** a sub-agent calls `read_screen`
- **THEN** it is refused as `permission`
#### Scenario: A task without a screen
- **WHEN** ABC-42 has no window and `read_screen` asks for it
- **THEN** it says ABC-42 has no screen and changes nothing
#### Scenario: A screen read outside its bounds
- **WHEN** `read_screen` asks for 0 or 1001 `lines`
- **THEN** it is refused naming `lines`
