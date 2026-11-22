# server-environment Specification

## Purpose
jagt's base image, the organisation's image built from it with the projects' toolchains, and the acceptance role on a server.

## Requirements

### Requirement: jagt's image carries no project toolchain
jagt's image SHALL hold jagt, git and tmux, and no compiler, runtime or agent CLI of a project. An organisation builds its own image
`FROM` it, adding its projects' toolchains (`examples/agent-image.Dockerfile`); sessions run in that image.

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
A repository's version file (`.tool-versions` or `mise.toml`) SHALL decide its tool versions, resolved by the image's version manager.
Two repositories in one image so use different versions.

#### Scenario: Two repositories
- **WHEN** one repository's version file names Java 17 and another's names Java 21
- **THEN** a session in each runs its own version, with nothing changed in the image

### Requirement: A project's commands name programs the image has
A start SHALL resolve the first word of every project's `verifyCommand` and `setupCommand`.
A program not found SHALL be one mark on the board's head, and jagt keeps running.
The mark SHALL name the project, the program and `Extend the image (docs/server.md)`.
It SHALL end on the first read that finds the program.
A program containing `/` SHALL NOT be checked.
An absent command SHALL be no mark.

#### Scenario: A verify program the image lacks
- **WHEN** a project's `verifyCommand` starts with a program the image lacks, and you start jagt
- **THEN** the board's head names the project and the program and tells to extend the image, and jagt keeps running

#### Scenario: No command
- **WHEN** a project sets neither `verifyCommand` nor `setupCommand`
- **THEN** the head says nothing about it

### Requirement: An MCP config file the container lacks is a mark on the board's head
A start SHALL read every file named by `claude.mcpConfig`, `assistant.mcpConfig` and `assistant.mcpConfigByKind`.
A file missing or unreadable SHALL be one mark on the board's head naming the key and the file, and jagt keeps running.
The mark SHALL end on the first read that finds the file.
An empty value, or a value holding the JSON itself, SHALL be no mark.

#### Scenario: A config file the container lacks
- **WHEN** `claude.mcpConfig` names a file the container lacks, and you start jagt
- **THEN** the board's head names the key and the file, and jagt keeps running

### Requirement: The image seeds the agent's folder on every start
When `JAGT_AGENT_SEED` and `JAGT_AGENT_HOME` are set and the seed folder exists, the entrypoint SHALL copy the seed over
`JAGT_AGENT_HOME` before jagt starts. Settings, MCP config and plugins SHALL be replaced; other files there SHALL be kept.

#### Scenario: A restart on a volume
- **WHEN** the container restarts with HOME on the volume and a seed folder baked in the image
- **THEN** the agent's settings and plugins are the image's, and its conversations and logins are unchanged

### Requirement: The image sizes jagt's JVM for a shared pod
The image SHALL start jagt's JVM with the options in `JAGT_JAVA_OPTIONS`: a heap of at most a quarter of the container's memory,
a serial collector, exit on an out-of-memory error, and a heap dump under `/data/dumps`.
The environment SHALL override the variable. Programs a session starts SHALL NOT receive the options.

#### Scenario: The image's default
- **WHEN** the container starts with `JAGT_JAVA_OPTIONS` unset
- **THEN** jagt's JVM runs with the image's options

#### Scenario: An override
- **WHEN** the container starts with `JAGT_JAVA_OPTIONS` set
- **THEN** jagt's JVM runs with that value alone

### Requirement: The server starter sits behind a TLS ingress
`jagt.server.yml.dist` SHALL set `server.forward-headers-strategy: native`, `server.servlet.session.cookie.secure: true` and
`server.servlet.session.timeout: 30m`. `examples/kubernetes.yml`'s Ingress SHALL carry a `tls` block for its host with a Secret of its own.
#### Scenario: A login behind the ingress
- **WHEN** the ingress ends TLS and forwards `X-Forwarded-Proto: https` and `X-Forwarded-For`
- **THEN** redirects keep `https`, the session cookie is Secure, and a login's peer address is the client's
#### Scenario: An idle board
- **WHEN** a login is idle for 30 minutes
- **THEN** its session ends and the next request lands on the login page

### Requirement: The acceptance flows name a program the image has
The program read SHALL also resolve the first word of an enabled acceptance role's `flows.command` while its `flows.driver` is playwright.
A program not found SHALL be one mark on the board's head naming the role, the program and `Extend the image (docs/server.md)`.
It SHALL end on the first read that finds the program.

#### Scenario: A flows program the image lacks
- **WHEN** acceptance's driver is playwright and its `flows.command` starts with `npx`, which the image lacks
- **THEN** the board's head names the role and the program and tells to extend the image

#### Scenario: Flows off
- **WHEN** acceptance's `flows.driver` is none
- **THEN** the head says nothing about its command

### Requirement: The agent's home is on the volume
The image SHALL set `HOME` to `/data/home`, created at every start, so a session's conversation and login outlive the container.

#### Scenario: A pod is replaced
- **WHEN** the pod is deleted and a new one starts on the same volume
- **THEN** a revived session finds its conversation under `HOME`

### Requirement: The configuration is on the volume
The image SHALL read and write `jagt.yml` at `/data/jagt.yml`.
A start finding no file there SHALL copy the image's `jagt.server.yml.dist` to it; a start finding one SHALL leave it alone.

#### Scenario: A project added, then a recreate
- **WHEN** a project is added from the board and the container is recreated on the same volume
- **THEN** the project is still in `jagt.yml`

#### Scenario: A first start
- **WHEN** the volume holds no `jagt.yml`
- **THEN** the image's starter is copied there
#### Scenario: A first start after an upgrade
- **WHEN** the volume holds no `jagt.yml` and one is mounted at `/app/jagt.yml`
- **THEN** the mounted file is copied there instead of the starter
