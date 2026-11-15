## REMOVED Requirements

### Requirement: A project is edited and removed
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

## ADDED Requirements

### Requirement: A project is edited or removed
A project's block in `jagt.yml` SHALL be rewritten or deleted as lines (`path`, `baseBranch`, `deployBranch`,
`verifyCommand`, `about`, `labels`, `humanPaths` are rewritten; its other keys, its comments and every other line
stay). The file is read back and put back when it no longer reads. A project an open task works in, and the only
project, are not removed.

#### Scenario: Editing one project
- **WHEN** the human changes the deploy branch of `billing`, whose block also holds `codeHost` and a comment
- **THEN** only `billing`'s managed keys change; `codeHost`, the comment and the other projects stay

#### Scenario: A project an open task uses
- **WHEN** the human removes `billing` while ABC-42 is not done and works in it
- **THEN** the removal is refused naming ABC-42, and `jagt.yml` is unchanged

#### Scenario: Removing one project
- **WHEN** the human removes `billing`, with `web` below it under a comment
- **THEN** `billing`'s block is gone, and the comment above `web` stays

### Requirement: A project file that cannot be written is refused by hand
A file that cannot be written SHALL be refused with a sentence naming the file to edit by hand, and stays as it was.
The refusal is a request the human answers, never a server fault; no write is tried on a read-only file.

#### Scenario: A file that cannot be written
- **WHEN** the folder holding `jagt.yml` is read-only and the human adds a project
- **THEN** the add is refused as an invalid request naming `<file>`, and the file's bytes are as they were

#### Scenario: A read-only jagt.yml
- **WHEN** `jagt.yml` is read-only, as on a single-file mount, and the human adds, edits or removes a project
- **THEN** the board shows a sentence naming the file to edit, nothing is thrown, and no temp file is written
