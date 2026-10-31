## ADDED Requirements

### Requirement: A project is edited and removed
A project's block in `jagt.yml` SHALL be rewritten or deleted as lines (`path`, `baseBranch`, `deployBranch`,
`verifyCommand`, `about` are rewritten; its other keys, its comments and every other line stay). The file is read back
and put back when it no longer reads. A project an open task works in, and the only project, are not removed.
A file that cannot be written is refused naming it, and stays as it was.

#### Scenario: Editing one project
- **WHEN** the human changes the deploy branch of `billing`, whose block also holds `labels` and a comment
- **THEN** only `billing`'s managed keys change; `labels`, the comment and the other projects stay

#### Scenario: A project an open task uses
- **WHEN** the human removes `billing` while ABC-42 is not done and works in it
- **THEN** the removal is refused naming ABC-42, and `jagt.yml` is unchanged

#### Scenario: Removing one project
- **WHEN** the human removes `billing`, with `web` below it under a comment
- **THEN** `billing`'s block is gone, and the comment above `web` stays

#### Scenario: A file that cannot be written
- **WHEN** the folder holding `jagt.yml` is read-only and the human adds a project
- **THEN** the add is refused with `Cannot change <file>` and its cause, and the file's bytes are as they were
