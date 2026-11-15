## ADDED Requirements

### Requirement: The Master's token is written beside state.json
jagt SHALL write the Master's token to `master-token` beside `state.json`, mode 0600, never into the root.
A read-only root (an installed jar, a mounted config) still starts. A session's shell line naming that file SHALL be
refused (`ToolGate`).

#### Scenario: A read-only root
- **WHEN** the root is read-only and the state folder writable
- **THEN** jagt starts and the Master reaches `/mcp` with its token

#### Scenario: A session names the token
- **WHEN** a session's shell line names the token's path
- **THEN** `ToolGate` refuses it
