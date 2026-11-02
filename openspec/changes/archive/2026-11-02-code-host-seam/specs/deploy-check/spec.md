## ADDED Requirements

### Requirement: The code host is chosen
jagt SHALL read a deploy's pipeline through the driver `codeHost` names, a project's key over `orchestrator.codeHost` over `github`.
The drivers are `github` (`gh`), `gitlab` (`glab`), `command` and `none`. jagt MUST NOT guess the kind from a host name or an address.
`codeHostName` names the host where the remote does not. `gh` is told the host with `-R <host>/<owner>/<repo>`.
`glab` keeps a port the remote has in `--hostname`. A CLI or command holds the credential, jagt none.

#### Scenario: No key
- **WHEN** a project and `orchestrator` name no `codeHost`
- **THEN** its pipelines are read through `gh`

#### Scenario: A host the name does not give away
- **WHEN** `projects.proj.codeHost` is `gitlab` and the remote is `git@git.example.invalid:acme/shop.git`
- **THEN** its pipelines are read through `glab` on `git.example.invalid`

#### Scenario: An enterprise host
- **WHEN** the remote is `git@ghe.example.invalid:acme/shop.git` and `codeHost` is `github`
- **THEN** `gh` is run with `-R ghe.example.invalid/acme/shop`

#### Scenario: Any provider
- **WHEN** `codeHost` is `command` and `pipelineCommand` is `["ci-state", "{sha}"]`
- **THEN** it runs in the project's directory with the deploy commit
- **AND** what it prints, one of `success`, `failed`, `running`, `none`, `unknown`, is the pipeline
- **AND** anything else leaves the deploy open as unread, naming `pipelineCommand`

#### Scenario: No code host
- **WHEN** `codeHost` is `none`
- **THEN** no pipeline is read and the deploy stands as for a repository with no code-host address

### Requirement: A driver tells its host's words in jagt's five
Each driver SHALL tell what its host says of a pipeline in five words: `success`, `failed`, `running`, `none`, `unknown`.
It SHALL tell each failed job as infrastructure or code. No host's wording passes it.

#### Scenario: A cancelled run
- **WHEN** the host says a run was cancelled
- **THEN** the driver tells it `failed`

#### Scenario: A run that never started
- **WHEN** the host says a run's conclusion is `startup_failure`
- **THEN** its failed job is told as infrastructure

### Requirement: Startup finds the selected code host missing
Startup SHALL refuse a selected `gh` or `glab` that cannot be found, naming the `codeHost` key.
It SHALL refuse a `command` whose `pipelineCommand` is unset or whose program cannot be found, naming that key.
It MUST NOT ask the network, and checks only what is selected.

#### Scenario: A CLI missing
- **WHEN** `codeHost` is `gitlab` and `glab` is on no path
- **THEN** jagt refuses to start naming `orchestrator.codeHost`

#### Scenario: A kind nobody answers for
- **WHEN** `codeHost` is `bitbucket`
- **THEN** jagt refuses to start naming the key and the kinds it knows

## MODIFIED Requirements

### Requirement: A deploy stands with no pipeline where none can come
A deploy SHALL stand with no pipeline where none can come.
That is: the host lists none for `NO_PIPELINE_AFTER` (10 minutes) after the repository's recorded push, the repository has no
code-host address, or its `codeHost` is `none`. A target that stood on the first SHALL be read on while `DEPLOYED`.

#### Scenario: No pipeline will come
- **WHEN** the host lists no pipeline for ABC-42's deploy commit 10 minutes after its recorded push
- **OR** its repository's remote is a local path, or its `codeHost` is `none`
- **THEN** the deploy stands and its session is asked once; within the 10 minutes the line says the pipeline is not started
- **AND** one INFO says `deploy settled without a pipeline`, naming the cause
- **AND** a pipeline the host lists later is read while ABC-42 is `DEPLOYED`; red on the code makes it `DEPLOY_CONFLICT`
