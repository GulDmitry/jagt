## MODIFIED Requirements

### Requirement: A project is added from the board
Add a project SHALL read a repository path as it is typed: a repository, its origin, the branch tasks are cut
from, the remote's other branches as last fetched, a key no project holds. It fills the fields the human has not typed.
It writes one entry under `projects:` in
`jagt.yml`, leaving every other line as it was. A path that is not ready is refused and nothing is written.
Nothing is fetched.
A repository listing no remote at all is read from its local branches: `main` or `master` is the base, the rest are deploy branches.
It is saved with `remote: none`. A repository with a remote is read as before.

#### Scenario: A folder that is no repository
- **WHEN** the human types the path of a plain folder and adds it
- **THEN** the reading says it is not a git repository, the add is refused, and `jagt.yml` is unchanged

#### Scenario: The remote's branches
- **WHEN** the remote holds `main` (the base), `feature-x`, `staging` and `develop`
- **THEN** the deploy field is offered `feature-x`, `staging` and `develop`, prefilled `develop`, never the base

#### Scenario: A verify command the repository carries
- **WHEN** the repository holds `mvnw`, or a `Makefile` with a `test:` target and no wrapper script
- **THEN** the verify field is `./mvnw test`, or `make test`; a `Makefile` without a `test:` target suggests nothing

#### Scenario: A repository with no remote
- **WHEN** the human adds a repository with no remote, holding `main` and `dev`
- **THEN** the base is `main`, `dev` is offered as deploy branch, and the entry holds `remote: "none"`
