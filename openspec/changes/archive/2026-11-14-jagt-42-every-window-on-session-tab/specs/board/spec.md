## ADDED Requirements

### Requirement: Every window jagt opens is on the Session tab
A window jagt opens SHALL show on the Session tab and take `say`, whether a task's session or not: a login and the
acceptance scenarios session. The tab lists the open task's session first, then every such window by name.

#### Scenario: A window that is no task's
- **WHEN** jagt opens a login or the scenarios window
- **THEN** the Session tab lists it, shows its screen as it reads now, and a line typed under it reaches that window

## MODIFIED Requirements

### Requirement: A lost reach of a code host is the board's head
Where jagt cannot reach a code host it reads, the header SHALL show ONE mark naming driver and host. Its hover gives
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
