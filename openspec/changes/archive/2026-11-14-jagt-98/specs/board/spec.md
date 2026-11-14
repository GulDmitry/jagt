## MODIFIED Requirements

### Requirement: A lost reach of a code host is the board's head
Where jagt cannot reach a code host or an MCP server, the header SHALL show ONE mark naming driver and host. Its hover gives
the fix command and the time left to the next probe, if scheduled. It replaces the per-card `DEPLOY_UNREAD` stop on that
cause and wears the broken colour. `login` SHALL open the driver's login as a window's process, never by keystroke,
and that window is on the Session tab. A login jagt opens SHALL open no browser: the board shows the login's one-time
code and device URL whole. Where the driver's CLI offers no device flow, its own login prompt shows on the Session
tab, and `say` answers it.
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
#### Scenario: A login with a device flow starts
- **WHEN** a code host's CLI offers a device flow and its login starts
- **THEN** the board shows the one-time code and the device URL whole, and jagt opens no browser
#### Scenario: A login without a device flow
- **WHEN** a code host's CLI offers no device flow
- **THEN** its own login prompt shows on the Session tab, and `say` answers it
#### Scenario: The assistant's MCP server needs a login
- **WHEN** a failed read finds an MCP server down
- **THEN** the header's mark opens `claude` as the login window on the Session tab, and `say` types `/mcp` into it
- **AND** while the mark stands jagt asks the CLI again each minute, and the read finding every server connected clears it
