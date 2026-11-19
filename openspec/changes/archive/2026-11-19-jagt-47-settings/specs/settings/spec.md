## ADDED Requirements

### Requirement: A gear on the board opens the settings
The gear's `jagt.yml settings` entry SHALL open one page of the basic settings.
It shows projects, review and deploy (`codeReview`, `autoReview`, `infraWait`), tracker, Master mode and notifications.
It shows no role or model setting and no secret value, only env var names. The project form is the page's, not a second copy.

#### Scenario: The page opens
- **WHEN** the gear is pressed
- **THEN** the page lists projects, review and deploy, tracker, Master mode and notifications, and no role or model

#### Scenario: A project is added
- **WHEN** a project is added on the page
- **THEN** `jagt.yml` holds it and the board lists it

### Requirement: A change is written in place and a failure is said
A changed key SHALL go through `ConfigWriter.set`. Its comment and the key order stay; an inline section is written as a block.
A failed write SHALL be answered with its reason and said on the board, the last good file back.
A file that cannot be written is 409 `NOT_WRITABLE`; a failed write or a result that did not read is 500.

#### Scenario: A key is changed
- **WHEN** auto-review is switched off on the page, `autoReview` being written inline
- **THEN** `autoReview` is a block holding `enabled: false`, and the toast says what changed

#### Scenario: The file cannot be written
- **WHEN** a key is changed while `jagt.yml` is not writable
- **THEN** the board says the file is not writable and the control shows what the file holds

### Requirement: A setting needing a restart offers the restart
A change that is read only at start SHALL say so on the page and offer the existing `restart` verb.

#### Scenario: A restart is needed
- **WHEN** the pipeline re-run waits are changed
- **THEN** the page says it applies after a restart and offers the restart verb
