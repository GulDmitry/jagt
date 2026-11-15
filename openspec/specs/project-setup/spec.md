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

### Requirement: A repository with no remote is read from its local branches
A repository listing no remote at all SHALL be read from its local branches: `main` or `master` is the base, the rest are deploy branches.
It SHALL be saved with `remote: none`. A repository with a remote is read as before.

#### Scenario: A repository with no remote
- **WHEN** the human adds a repository with no remote, holding `main` and `dev`
- **THEN** the base is `main`, `dev` is offered as deploy branch, and the entry holds `remote: "none"`

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

### Requirement: Labels and human paths are typed in the form
The form SHALL carry `labels` and `humanPaths`, each typed as a comma-separated list, blanks dropped; an empty field
writes no key. The edit form shows what `jagt.yml` holds for both.

#### Scenario: Labels and human paths typed
- **WHEN** the human adds `billing` with labels `bill, pay` and human paths `**/auth/**`
- **THEN** `billing`'s block holds `labels: ["bill","pay"]` and `humanPaths: ["**/auth/**"]`

### Requirement: A project is edited from the board
`project edit <key>` SHALL open the add form filled with what `jagt.yml` holds for `<key>`. Its key is fixed.
Saving rewrites that project's block. The form reads its path without counting the project itself as another
one pointing there.

#### Scenario: Editing from the palette
- **WHEN** the human types `project edit billing`
- **THEN** the form opens titled for `billing`, with its path, branches and check command in the fields

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

### Requirement: A project named by url is cloned
A project naming `url` SHALL use its `path` as is when that folder exists, and nothing is cloned.
With no `path`, or a missing one, jagt SHALL clone the url into `repos/<project>` under the data folder. It does so at start, which waits for it.
The git CLI's own credentials serve. A clone that fails SHALL be ONE mark on the board's head naming the project and the cause, never a card.
The other projects work, the clone is tried again each minute, and the mark ends with the clone.
#### Scenario: A url with no path
- **WHEN** a project names a url and no path
- **THEN** jagt clones it into `repos/<project>` at start and a task starts on it
#### Scenario: A path that exists
- **WHEN** a project names a url and a path that exists
- **THEN** that path is used and nothing is cloned
#### Scenario: A url that cannot be cloned
- **WHEN** one project's clone fails and another project names a repository that exists
- **THEN** the board's head names the failing project and the cause, and a task starts on the other project
- **AND** the mark ends when a later try clones it
