## MODIFIED Requirements

### Requirement: Loopback, or behind a password login
`orchestrator.auth` SHALL be `none` (default: loopback, no login) or `password` (one bcrypt hash in `jagt.yml`, never logged).
`none` with a `server.address` beyond loopback, `auth.allowedHosts` or a non-loopback `server.publicUrl` refuses the start naming `auth`.
`password` guards the board, `/api` and `/api/events`: a browser is sent to `/login.html`, `/api` answers 401. `/mcp` and `/api/agent/` take no login;
off loopback or forwarded, they answer 401 without the Master token.
#### Scenario: No login
- **WHEN** `auth` is `password` and a browser opens the board without logging in
- **THEN** it is sent to the login page and `/api` answers 401
#### Scenario: A session
- **WHEN** `auth` is `password` and a session calls `/mcp` with the worktree header
- **THEN** it is served with no login from loopback, and refused 401 off loopback or forwarded without the Master token
#### Scenario: A second machine
- **WHEN** `auth` is `none` and `server.address` is not loopback, or `server.publicUrl` is `https://jagt.example.com`
- **THEN** the start is refused naming `auth`

### Requirement: A foreign request is refused
`LoopbackFilter` SHALL refuse a foreign Host or Origin and `/mcp` anything but JSON, unless its host is the `publicUrl`'s or in `auth.allowedHosts`;
`BoardWriteFilter` any write without Origin but the two above.
#### Scenario: Behind an ingress, or a stranger posts
- **WHEN** a request's Host is `server.publicUrl`'s or is in `auth.allowedHosts`
- **THEN** `LoopbackFilter` lets it through
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused 403; `ToolGate` stops the `curl` first
