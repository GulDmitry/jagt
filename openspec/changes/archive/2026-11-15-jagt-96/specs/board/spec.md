## MODIFIED Requirements

### Requirement: Loopback, or behind a login
`orchestrator.auth` SHALL be `none` (default: loopback, no login) or `password` (one bcrypt hash in `jagt.yml`, never logged).
`none` with a `server.address` beyond loopback, or with `auth.allowedHosts`, refuses the start naming `auth`. `password` guards the
board, `/api` and `/api/events`: a browser is sent to `/login.html`, `/api` answers 401. `/mcp` and `/api/agent/` stay outside the login: a peer on
loopback is named by its worktree header or the Master token, a peer off loopback is refused 401 without the Master token. `LoopbackFilter` SHALL
refuse a foreign Host or Origin and `/mcp` anything but JSON, unless the host is in `auth.allowedHosts`; `BoardWriteFilter` any write without Origin
but `/mcp` and `/api/agent/` as routed.
#### Scenario: No login
- **WHEN** `auth` is `password` and a browser opens the board without logging in
- **THEN** it is sent to the login page and `/api` answers 401
#### Scenario: A session
- **WHEN** `auth` is `password` and a session calls `/mcp` with the worktree header
- **THEN** it is served with no login from loopback, and refused 401 from a peer off loopback unless it presents the Master token
#### Scenario: A second machine
- **WHEN** `auth` is `none` and `server.address` is not loopback
- **THEN** the start is refused naming `auth`
#### Scenario: Behind an ingress
- **WHEN** a request's Host is in `auth.allowedHosts`
- **THEN** `LoopbackFilter` lets it through
#### Scenario: A stranger posts
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused 403; `ToolGate` stops the `curl` first
