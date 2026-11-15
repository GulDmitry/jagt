## MODIFIED Requirements

### Requirement: One surface, one projection
The board, served by the jar, SHALL be the one front-end. Its projection is `flow/Move` + `flow/view/TaskView`, built by `service/task/TaskViews`,
rendered by `/api/tasks`; gate and button read one `FlowRules.allows`; `TaskViews.snapshot()` reads the configuration once a render. A second surface MUST add
no second answer to a board question. Stopping the backend is no verb.
#### Scenario: Ship
- **WHEN** `ship` runs
- **THEN** it asks its button's `FlowRules`

### Requirement: Every window jagt opens is on the Session tab
A window jagt opens SHALL show on the Session tab and take `say`, whether a task's session or not: a login and the
acceptance scenarios session. The tab lists the open task's session first, then every such window by name.
#### Scenario: A window that is no task's
- **WHEN** jagt opens a login or the scenarios window
- **THEN** the Session tab lists it, shows its screen as it reads now, and a line typed under it reaches that window

## REMOVED Requirements

### Requirement: Loopback, or behind a login
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

### Requirement: A lost reach of a code host is the board's head
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

## ADDED Requirements

### Requirement: Loopback, or behind a password login
`orchestrator.auth` SHALL be `none` (default: loopback, no login) or `password` (one bcrypt hash in `jagt.yml`, never logged).
`none` with a `server.address` beyond loopback, or with `auth.allowedHosts`, refuses the start naming `auth`. `password` guards the
board, `/api` and `/api/events`: a browser is sent to `/login.html`, `/api` answers 401. `/mcp` and `/api/agent/` take no login;
off loopback or forwarded, they answer 401 without the Master token.
#### Scenario: No login
- **WHEN** `auth` is `password` and a browser opens the board without logging in
- **THEN** it is sent to the login page and `/api` answers 401
#### Scenario: A session
- **WHEN** `auth` is `password` and a session calls `/mcp` with the worktree header
- **THEN** it is served with no login from loopback, and refused 401 off loopback or forwarded without the Master token
#### Scenario: A second machine
- **WHEN** `auth` is `none` and `server.address` is not loopback
- **THEN** the start is refused naming `auth`

### Requirement: A lost reach of a code host shows in the board's head
Where jagt cannot reach a code host or an MCP server, the header SHALL show ONE mark naming driver and host. Its hover gives
the fix command and the time left to the next probe, if scheduled. It replaces the per-card `DEPLOY_UNREAD` stop on that
cause and wears the broken colour. `login` SHALL open the driver's login as a window's process, never by keystroke,
and that window is on the Session tab.
#### Scenario: The code host's CLI is signed out
- **WHEN** the code host's CLI is signed out of `git.example.invalid` while ABC-42 and ABC-43 are deployed
- **THEN** no card stops, and the header says the CLI is not logged in to `git.example.invalid`
- **AND** its hover gives the driver's login command for `git.example.invalid`
#### Scenario: The human logs in
- **WHEN** the human presses the mark and signs in
- **THEN** the next deploy read through that CLI on that host clears the mark, with no restart
#### Scenario: No command can fix it
- **WHEN** the CLI is not installed
- **THEN** the mark names it and is not pressable
#### Scenario: The assistant's MCP server needs a login
- **WHEN** a failed read finds an MCP server down
- **THEN** the header's mark opens `claude` as the login window on the Session tab, and `say` types `/mcp` into it
- **AND** while the mark stands jagt asks the CLI again each minute, and the read finding every server connected clears it

### Requirement: A foreign request is refused
`LoopbackFilter` SHALL refuse a foreign Host or Origin and `/mcp` anything but JSON, unless the host is in `auth.allowedHosts`;
`BoardWriteFilter` any write without Origin but the two above.
#### Scenario: Behind an ingress
- **WHEN** a request's Host is in `auth.allowedHosts`
- **THEN** `LoopbackFilter` lets it through
#### Scenario: A stranger posts
- **WHEN** a foreign page, a rebound name, a session's `curl` or a respelled `/api;x/…` posts a verb
- **THEN** refused 403; `ToolGate` stops the `curl` first

### Requirement: A login jagt opens shows its code whole
A login jagt opens SHALL open no browser: the board shows the login's one-time
code and device URL whole. Where the driver's CLI offers no device flow, its own login prompt shows on the Session
tab, and `say` answers it.
#### Scenario: A login with a device flow starts
- **WHEN** a code host's CLI offers a device flow and its login starts
- **THEN** the board shows the one-time code and the device URL whole, and jagt opens no browser
#### Scenario: A login without a device flow
- **WHEN** a code host's CLI offers no device flow
- **THEN** its own login prompt shows on the Session tab, and `say` answers it
