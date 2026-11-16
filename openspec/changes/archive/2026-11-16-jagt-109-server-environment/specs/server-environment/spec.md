## ADDED Requirements

### Requirement: jagt's image carries no project toolchain
jagt's image SHALL hold jagt, git and tmux, and no compiler, runtime or agent CLI of a project. An organisation builds its own image
`FROM` it, adding its projects' toolchains (`Dockerfile.dist`); sessions run in that image.

#### Scenario: The shipped image
- **WHEN** a session runs in jagt's image with nothing added
- **THEN** no project toolchain is present, and a check needing one fails naming the missing tool

#### Scenario: An organisation's image
- **WHEN** an image built `FROM` jagt's adds a toolchain
- **THEN** every session started from it has that toolchain on its path

### Requirement: A session checks its work in its own image
A session's compile, unit tests and lint SHALL run in the image the session itself runs in, against its worktree: the inner loop.

#### Scenario: A failing unit test
- **WHEN** a session changes code in its worktree and runs the repository's unit tests
- **THEN** they run in the session's container and report in seconds, before any push or deploy

### Requirement: Acceptance is the outer loop on a server
`jagt.server.yml.dist` SHALL enable the acceptance role, which checks the deployed environment against the ticket
([`acceptance`](../acceptance/spec.md)). It does not replace the inner loop.

#### Scenario: A change deployed
- **WHEN** a task's change is deployed and the server's `jagt.yml` is the shipped one
- **THEN** the acceptance role checks the environment once per deploy

### Requirement: jagt starts no container runtime
jagt SHALL NOT start a docker daemon, and SHALL NOT mount or reach the host's docker socket, for a session or a check.

#### Scenario: A session needs containers
- **WHEN** a repository's checks need a container runtime
- **THEN** jagt provides none; the check is the pipeline's or the deployed environment's

### Requirement: A repository names its own tool versions
A session SHALL get each repository's tool versions from that repository's version file (`.tool-versions` or `mise.toml`),
resolved by the image's version manager, so two repositories in one image use different versions.

#### Scenario: Two repositories
- **WHEN** one repository's version file names Java 17 and another's names Java 21
- **THEN** a session in each runs its own version, with nothing changed in the image
