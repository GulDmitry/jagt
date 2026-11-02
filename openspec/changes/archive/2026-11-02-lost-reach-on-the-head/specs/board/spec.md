## ADDED Requirements

### Requirement: jagt's lost reach is the board's head
Where jagt cannot reach a code host it reads, the header SHALL show ONE mark naming driver and host, its hover giving
the fix command; it replaces the per-card `DEPLOY_UNREAD` stop on that cause and wears the broken colour. `login`, a
`GlobalCommand`, SHALL open the driver's own login as a window's process, never by keystroke or credential.

#### Scenario: glab is signed out
- **WHEN** `glab` is signed out of `git.example.invalid` while ABC-42 and ABC-43 are deployed
- **THEN** no card stops, and the header says `glab not logged in to git.example.invalid`
- **AND** its hover says `fix: glab auth login --hostname git.example.invalid`

#### Scenario: The human logs in
- **WHEN** the human presses the mark and signs in
- **THEN** the next deploy read through `glab` on that host clears the mark, with no restart

#### Scenario: No command can fix it
- **WHEN** the CLI is not installed
- **THEN** the mark names it and is not pressable
