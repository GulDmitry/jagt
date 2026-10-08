## MODIFIED Requirements

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: arguments are read into the message and judged before
the tool runs. A field the message does not declare is ignored, so a CLI a version ahead is not rejected; one
misspelling a field the call left out is refused.

#### Scenario: An unknown field
- **WHEN** a call carries a field the message does not declare
- **THEN** ignored; the rest judged

#### Scenario: A misspelled field
- **WHEN** a call sends `task_id` and no `taskId`
- **THEN** it is refused naming `taskId`, never run on the caller's own task

### Requirement: A session's refusal is a correction
A refusal SHALL come back from the session's call, never a block or a human's question. `surface/mcp/ToolFailure` reads
what comes next off what the handler threw: `validation`, a wrong argument, is fixed and resent; `business` (a
`flow/Refusal`: what jagt holds refuses) and `permission` are the answer; `transient` alone is `retryable`.

#### Scenario: The board is full
- **WHEN** a session asks for a 25th task, or hands back owing its notes
- **THEN** it is refused as `business`, not `validation`
