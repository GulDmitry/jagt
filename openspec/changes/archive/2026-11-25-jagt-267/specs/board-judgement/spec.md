## MODIFIED Requirements

### Requirement: The log has a tail on the board
`tail` SHALL be a report: the last `LogTail.LINES` lines of jagt's own log, one readable line each (time, level,
message, fields), a line past `LogTail.MAX_LINE` characters cut and flagged. It reads on request and stores nothing.
The board lists them newest first as every log view does ([`board`](../board/spec.md)).

#### Scenario: Reading what failed
- **WHEN** you type `tail`
- **THEN** a dialog shows the newest lines of the log, newest first, each as `<time> <level> <message> <field>=<value>`

#### Scenario: No log
- **WHEN** the log file does not exist or is empty
- **THEN** the report says where the log would be and that it holds nothing yet
