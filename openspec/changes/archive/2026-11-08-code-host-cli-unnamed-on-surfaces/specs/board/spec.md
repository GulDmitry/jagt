## REMOVED Requirements

### Requirement: jagt's lost reach is the board's head
**Reason**: its scenarios named a vendor CLI; replaced by "A lost reach of a code host is the board's head".

## ADDED Requirements

### Requirement: A lost reach of a code host is the board's head
Where jagt cannot reach a code host it reads, the header SHALL show ONE mark naming driver and host. Its hover gives
the fix command and the time left to the next probe, if scheduled. It replaces the per-card `DEPLOY_UNREAD` stop on that
cause and wears the broken colour. `login` SHALL open the driver's login as a window's process, never by keystroke.
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
