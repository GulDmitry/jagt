## ADDED Requirements

### Requirement: Logging is a seam, file or console
`orchestrator.logging` SHALL select where jagt logs: `file` (default) writes the backend log and the Master's feed under `logs/` as before;
`console` writes no log file.
It writes every line, the Master's included, as one ECS JSON object on stdout: the event is the message, each field its own member.
Any other value SHALL be refused at start naming the key.
#### Scenario: Console logging
- **WHEN** `logging` is `console` and jagt logs a line with fields
- **THEN** stdout carries one JSON object for it with the event and every field, and no file is created under `logs/`
#### Scenario: Logging unset
- **WHEN** `logging` is unset or `file`
- **THEN** the log files are written under `logs/` as before
#### Scenario: An unknown value
- **WHEN** `logging` is `syslog`
- **THEN** the start is refused naming `orchestrator.logging`
