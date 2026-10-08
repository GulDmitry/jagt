## MODIFIED Requirements

### Requirement: A session's refusal is a correction
A refusal SHALL come back from the session's call, never a block or a human's question. `surface/mcp/ToolFailure` reads
what comes next off what the handler threw: `validation` is fixed and resent; `business` (a `flow/Refusal`, a vanished
task) and `permission` are the answer; `transient` alone is `retryable`.

#### Scenario: Refused
- **WHEN** a session's call is refused as `validation`
- **THEN** it fixes every line and calls again
