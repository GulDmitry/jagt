## MODIFIED Requirements

### Requirement: A project is added from the board
Add a project SHALL read a repository path as it is typed: a repository, its origin, the branch tasks are cut
from, the remote's other branches as last fetched, a key no project holds. It fills the fields the human has not typed.
The path field offers the folders under what is typed, repositories first. It writes one entry under `projects:` in
`jagt.yml`, leaving every other line as it was. A path that is not ready is refused and nothing is written.
Nothing is fetched. The verb `project` reaches it from `help` and the palette: typed alone it opens the form.

#### Scenario: A folder that is no repository
- **WHEN** the human types the path of a plain folder and adds it
- **THEN** the reading says it is not a git repository, the add is refused, and `jagt.yml` is unchanged

#### Scenario: The remote's branches
- **WHEN** the remote holds `main` (the base), `feature-x`, `staging` and `develop`
- **THEN** the deploy field is offered `feature-x`, `staging` and `develop`, prefilled `develop`, never the base

#### Scenario: A verify command the repository carries
- **WHEN** the repository holds `mvnw`, or a `Makefile` with a `test:` target and no wrapper script
- **THEN** the verify field is `./mvnw test`, or `make test`; a `Makefile` without a `test:` target suggests nothing

#### Scenario: Typing a folder prefix
- **WHEN** the human types `~/www/bi` and `~/www` holds the repository `billing`, a folder `bills` and a hidden `.cache`
- **THEN** the field offers `~/www/billing/` marked as a repository, then `~/www/bills/`, and never `.cache`

#### Scenario: The verb typed with a path
- **WHEN** the human types `project ~/www/billing` and the reading is ready
- **THEN** the entry is added with the key, base, deploy branch and verify command the reading suggests
- **AND** a path that is not ready is refused with its first problem, and `jagt.yml` is unchanged
