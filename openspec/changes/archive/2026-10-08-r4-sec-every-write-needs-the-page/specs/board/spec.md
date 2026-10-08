## MODIFIED Requirements

### Requirement: Loopback only
The board SHALL bind loopback, password-free. `LoopbackFilter` SHALL refuse a foreign Host or Origin and `/mcp`
anything but JSON; `BoardWriteFilter` any write without Origin but `/mcp` and `/api/agent/` as routed.

#### Scenario: A second machine
- **WHEN** jagt runs with `--server.address=0.0.0.0`
- **THEN** it is reachable elsewhere

#### Scenario: A stranger posts
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused 403; `ToolGate` stops the `curl` first
