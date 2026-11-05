## MODIFIED Requirements

### Requirement: A read on OpenCode is held to its role's scope
A read SHALL be offered `read`, `grep`, `glob`, `bash` and, where the role fetches, `webfetch`, and no MCP server.
Its permissions SHALL deny every command first, allow the role's, deny its refused ones, and deny any redirect last.
Every call SHALL first go to jagt's read gate through a plugin written beside `state.json` at each start.
A refused call or an unreachable gate denies the call, and the model retries.
A completed call no gate heard, or a completed read of a file the role refuses, discards the answer and answers failed.
Tokens SHALL be booked whatever the read answered, and a local failure SHALL NOT open the cloud's breaker.

#### Scenario: A call outside the scope
- **WHEN** a read's `git log > f` is put to the gate, or its `touch b.txt` was denied by the permissions
- **THEN** the call does not run, and the read goes on to an answer that stands

#### Scenario: The plugin never loaded
- **WHEN** a call completed and no call reached the gate
- **THEN** the answer is discarded and the check reports that it failed
