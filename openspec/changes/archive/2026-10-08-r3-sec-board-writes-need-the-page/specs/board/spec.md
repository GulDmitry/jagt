## MODIFIED Requirements

### Requirement: Loopback only
The board SHALL bind loopback, password-free. `LoopbackFilter` SHALL refuse a foreign Host or Origin and `/mcp`
anything but JSON; `BoardWriteFilter` an `/api` write without Origin, the Master token included.

#### Scenario: A second machine
- **WHEN** jagt runs with `--server.address=0.0.0.0`
- **THEN** it is reachable elsewhere

#### Scenario: A stranger posts
- **WHEN** a foreign page, a rebound name or a session's `curl` posts a verb
- **THEN** refused 403; `ToolGate` stops the `curl` first
