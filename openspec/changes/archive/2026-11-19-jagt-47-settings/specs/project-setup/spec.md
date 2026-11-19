## ADDED Requirements

### Requirement: A setting is changed in place
A key of `jagt.yml` SHALL be changed as lines. Only its value is rewritten.
Its trailing comment stays in its column, as do the comments and blank lines around it, the key order and every other line.
A key not written is added at the end of its section, a missing section with it.

#### Scenario: A value changes
- **WHEN** `postReviewReplies` is set to `false` in a `codeReview` block holding comments, a blank line and other keys
- **THEN** only that line changes, and its trailing comment stays on it

#### Scenario: A key not yet written
- **WHEN** `windowHours` is set under an `autoReview` block that does not hold it
- **THEN** it is added after the block's last key, before the comments that follow, and no other line moves

### Requirement: An inline entry is rewritten as a block
An entry written inline (`billing: {path: /srv/billing}`, or a whole `projects: {...}`) SHALL be rewritten as a block when changed.
Its keys and its trailing comment stay.

#### Scenario: An inline project is edited
- **WHEN** the human edits `billing`, written `billing: {path: /srv/billing, codeHost: github}`
- **THEN** `billing` is a block holding the edited keys and `codeHost`, and the other entries stay

## RENAMED Requirements
- FROM: `### Requirement: A project file that cannot be written is refused by hand`
- TO: `### Requirement: A failed write restores the last good file and says why`

## MODIFIED Requirements

### Requirement: A failed write restores the last good file and says why
A change to `jagt.yml` that cannot be written, or does not read afterwards, SHALL leave the last good file in place.
It is reported as a `ConfigWriteFailed` naming the file and the cause. No write is tried on a read-only file.
No text of jagt or its `.dist` files tells the human to edit by hand.

#### Scenario: A file that cannot be written
- **WHEN** the folder holding `jagt.yml` is read-only and the human adds a project
- **THEN** the add fails as a `ConfigWriteFailed` naming `<file>`, and the file's bytes are as they were

#### Scenario: A read-only jagt.yml
- **WHEN** `jagt.yml` is read-only, as on a single-file mount, and the human changes a setting
- **THEN** the board says the file is not writable, naming it, and no temp file is written

#### Scenario: A result that does not read
- **WHEN** the rewritten `jagt.yml` does not load
- **THEN** the last good file is back, and the board says it did not read and why

#### Scenario: No by-hand text
- **WHEN** the writer's code and the `.dist` files are searched for "by hand"
- **THEN** none is found
