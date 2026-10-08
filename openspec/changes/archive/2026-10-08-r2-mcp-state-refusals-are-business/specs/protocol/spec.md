## MODIFIED Requirements

### Requirement: A session's refusal is a correction
A refusal SHALL come back from the session's call, never a block or a human's question. `surface/mcp/ToolFailure` reads
what comes next off what the handler threw: `validation`, a wrong argument, is fixed and resent; `business` (a
`flow/Refusal`: what jagt holds refuses) and `permission` are the answer; `transient` alone is `retryable`.

#### Scenario: The board is full
- **WHEN** a session asks for a task while 24 are open
- **THEN** it is refused as `business`, not `validation`
