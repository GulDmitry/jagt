# project-setup Specification

## Purpose
A repository becomes a project from the board: read as its path is typed, written into the file the human keeps.

## Requirements

### Requirement: A project is added from the board
Add a project SHALL read a repository path as it is typed: a repository, its origin, the branch tasks are cut
from, the remote's other branches as last fetched, a key no project holds. It fills the fields the human has not typed.
It writes one entry under `projects:` in
`jagt.yml`, leaving every other line as it was. A path that is not ready is refused and nothing is written.
Nothing is fetched.

#### Scenario: A folder that is no repository
- **WHEN** the human types the path of a plain folder and adds it
- **THEN** the reading says it is not a git repository, the add is refused, and `jagt.yml` is unchanged

#### Scenario: The remote's branches
- **WHEN** the remote holds `main` (the base), `feature-x`, `staging` and `develop`
- **THEN** the deploy field is offered `feature-x`, `staging` and `develop`, prefilled `develop`, never the base

#### Scenario: A verify command the repository carries
- **WHEN** the repository holds `mvnw`, or a `Makefile` with a `test:` target and no wrapper script
- **THEN** the verify field is `./mvnw test`, or `make test`; a `Makefile` without a `test:` target suggests nothing

### Requirement: The path field and the verb reach the form
The path field SHALL offer the folders under what is typed, repositories first.
The verb `project` SHALL reach it from `help` and the palette: typed alone it opens the form.

#### Scenario: Typing a folder prefix
- **WHEN** the human types `~/www/bi` and `~/www` holds the repository `billing`, a folder `bills` and a hidden `.cache`
- **THEN** the field offers `~/www/billing/` marked as a repository, then `~/www/bills/`, and never `.cache`

#### Scenario: The verb typed with a path
- **WHEN** the human types `project ~/www/billing` and the reading is ready
- **THEN** the entry is added with the key, base, deploy branch and verify command the reading suggests
- **AND** a path that is not ready is refused with its first problem, and `jagt.yml` is unchanged

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

### Requirement: A project is edited from the board
`project edit <key>` SHALL open the add form filled with what `jagt.yml` holds for `<key>`. Its key is fixed.
Saving rewrites that project's block. The form reads its path without counting the project itself as another
one pointing there.

#### Scenario: Editing from the palette
- **WHEN** the human types `project edit billing`
- **THEN** the form opens titled for `billing`, with its path, branches and check command in the fields
