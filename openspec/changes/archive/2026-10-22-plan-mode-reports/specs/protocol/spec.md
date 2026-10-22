## ADDED Requirements

### Requirement: A tool says whether it changes anything
`McpToolRegistry` SHALL take an `Effect` for every tool, and `tools/list` SHALL carry `readOnlyHint` from it. A read, or a
report to jagt, SHALL be `REPORTS`; every other tool SHALL be `CHANGES`.

#### Scenario: A session where only calls that change nothing go through
- **WHEN** a session runs where only calls that change nothing go through
- **THEN** it still reports its status and its question to the board
