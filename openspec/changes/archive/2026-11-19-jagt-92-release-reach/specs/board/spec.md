## MODIFIED Requirements

### Requirement: A lost reach shows in the board's head
Where jagt cannot reach a code host, an MCP server or the release feed, the header SHALL show ONE mark naming driver
and host. Its hover gives the fix command and the next probe's time, if scheduled. It replaces the per-card `DEPLOY_UNREAD`
stop on that cause and wears the broken colour. `login` SHALL open the driver's login as a Session tab window's process, never a keystroke.
#### Scenario: The code host's CLI is signed out
- **WHEN** the code host's CLI is signed out of `git.example.invalid` while ABC-42 and ABC-43 are deployed
- **THEN** no card stops; the header says the CLI is not logged in there, its hover the driver's login command
#### Scenario: The human logs in
- **WHEN** the human presses the mark and signs in
- **THEN** the next deploy read through that CLI on that host clears the mark, with no restart
#### Scenario: No command can fix it
- **WHEN** the CLI is not installed
- **THEN** the mark names it and is not pressable
#### Scenario: The assistant's MCP server needs a login
- **WHEN** a failed read finds an MCP server down
- **THEN** the mark opens `claude` as the Session tab's login window, `say` types `/mcp`; a minutely read finding all connected clears it
#### Scenario: The release feed cannot be read
- **WHEN** a release check cannot read the feed
- **THEN** the head mark names its driver and host, and the next good read clears it
