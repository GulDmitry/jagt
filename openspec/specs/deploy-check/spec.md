# deploy-check Specification

## Purpose

When a deploy stands: the pipeline jagt reads on each deploy commit, who checks the deployed change, and a red deploy pipeline.

## Requirements

### Requirement: A deploy stands on the pipeline jagt reads on its commit
A deploy SHALL stand only once the host's pipeline on each target's recorded tip, its last fix else its merge, is green.
`DeployCheckJob` reads it every minute through the host's own CLI, not a model, until the deploy is asked about
([0109](../../../docs/decisions/0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md)).
A pipeline running, not started, unknown or unreadable keeps the deploy open, and the task's line says `waiting on its pipeline`.

#### Scenario: The deploy pipeline is not green yet
- **WHEN** ABC-42 is `DEPLOYED` and the pipeline on its deploy commit is running, not started or cannot be read
- **THEN** its session is not asked and the task stays `DEPLOYED`
- **AND** its line reads `deployed to dev — waiting on its pipeline: proj 01234567: pipeline running`

#### Scenario: The deploy pipeline goes green
- **WHEN** the pipeline on every deploy commit of ABC-42 reads green
- **THEN** its session is asked once, and the host is read no more for that deploy

### Requirement: A deploy stands with no pipeline where none can come
A deploy SHALL stand with no pipeline where none can come.
That is: the host lists none for `NO_PIPELINE_AFTER` (10 minutes) after the repository's recorded push, the repository has no
code-host address, or its `codeHost` is `none`. A `remote` of `none` implies that last one.
A target that stood on the first SHALL be read on while `DEPLOYED`.

#### Scenario: No pipeline will come
- **WHEN** the host lists no pipeline for ABC-42's deploy commit 10 minutes after its recorded push
- **OR** its repository's remote is a local path, or its `codeHost` is `none`
- **THEN** the deploy stands and its session is asked once; within the 10 minutes the line says the pipeline is not started
- **AND** one INFO says `deploy settled without a pipeline`, naming the cause
- **AND** a pipeline the host lists later is read while ABC-42 is `DEPLOYED`; red on the code makes it `DEPLOY_CONFLICT`

### Requirement: An unreadable pipeline host stops the deploy
A host that does not answer keeps the deploy open. A read that reached the host and could not list a pipeline counts:
a timeout, a 5xx, output that is not JSON. Three reads running SHALL stop it as `DEPLOY_UNREAD`.
The first read listing every pipeline, run while that stop stands, SHALL end it.

#### Scenario: The deploy pipeline cannot be listed
- **WHEN** the host answers 500 three reads running on ABC-42's deploy commit
- **THEN** ABC-42 stops as `DEPLOY_UNREAD`; the first read listing every pipeline ends the stop

### Requirement: A deploy waits on a reach jagt lost
A read jagt could not make at all SHALL keep the deploy open as `waiting on its pipeline`.
Such a read is a CLI missing, signed out or refused, or a host unreachable. It counts toward no stop and resets no count.
That is jagt's lost reach: held per driver and host in memory, ended by the next read through that pair that succeeds.
A retry waiting for its time stays armed through it.

#### Scenario: The CLI is signed out
- **WHEN** `glab` is signed out for three reads running
- **THEN** ABC-42 stays deployed, its line says `waiting on its pipeline`, and the header carries the lost reach

#### Scenario: Reads go on while reach stands
- **WHEN** reach to `git.example.invalid` is lost and the human signs in
- **THEN** the next tick's read lists the pipeline and clears it

### Requirement: A deploy pipeline red on infrastructure is retried
A pipeline red on infrastructure SHALL be retried and counted as a request's is, and stops the task as `INFRA` after the last retry.

#### Scenario: The deploy pipeline dies on infrastructure
- **WHEN** the pipeline on ABC-42's deploy commit went red with every failed job on infrastructure
- **THEN** the deploy stays open and its line names the retry
- **AND** after the last retry the task stops as `INFRA`

### Requirement: A deploy pipeline red on the code goes to the session
A pipeline red on the code SHALL hand the deploy worktree to the session as a deploy conflict is handed
([task-flow](../task-flow/spec.md#requirement-in-act-a-deploy-conflict-is-the-sessions)): `DEPLOY_CONFLICT`, cut from the deploy branch as it stands.
A fix staged there SHALL be committed and pushed by the next `deploy`, never by the session; the pipeline is then read on the commit it pushed.

#### Scenario: The deploy pipeline is red on the code
- **WHEN** a failed job of the pipeline on ABC-42's deploy commit failed its script
- **THEN** ABC-42 is `DEPLOY_CONFLICT`, its line reads `red pipeline on the deploy commit: proj 01234567`, and its deploy worktree is cut from `dev`
- **AND** where the Master acts the session is handed that worktree once; in `judge` the card says NEEDS YOU and nothing is pressed

#### Scenario: The session fixes the merge result
- **WHEN** the session stages its fix in full in the deploy worktree
- **THEN** jagt commits it and pushes it to `dev` through `deploy`, and ABC-42 is `DEPLOYED` on the new commit
- **AND** the pipeline on that commit is read, and the session is asked once it is green

#### Scenario: A press before the fix
- **WHEN** a `deploy` is pressed on ABC-42 while nothing is staged in its fix worktree
- **THEN** it is refused, saying the deploy waits on the session's fix in that worktree
- **AND** the worktree and the status stay

#### Scenario: The deploy worktree cannot be opened
- **WHEN** the pipeline is red on the code and the worktree path holds a checkout jagt did not cut
- **THEN** the deploy stays open, its line says why, and the next read tries again

### Requirement: Every deploy is checked where it landed
With `orchestrator.roles.qa.enabled` off, `DeployCheckJob` SHALL ask the session once per deploy commit, keyed on the task's turn.
Neither another relay nor a restart asks again; what it asks is the brief's `deploy` step.
Green MUST report nothing, the task staying `DEPLOYED`; red is the session's `IN_PROGRESS`, the round running again:
reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`

### Requirement: The session is asked only on a green pipeline
`DeployCheckJob` SHALL ask the session only once the pipeline on each deploy commit is green
([above](#requirement-a-deploy-stands-on-the-pipeline-jagt-reads-on-its-commit)).

#### Scenario: A pipeline not green yet
- **WHEN** the pipeline on ABC-42's deploy commit is running
- **THEN** the session is not asked

### Requirement: With QA on, QA checks the deploy instead of the session
With it on, QA checks the deploy ([qa](../qa/spec.md)) and `DeployCheckJob` MUST ask the session nothing; never both on one deploy.

#### Scenario: QA checks the deploy
- **WHEN** a deploy lands with `orchestrator.roles.qa.enabled` on
- **THEN** `deploy` is never sent to the session and QA's check runs

### Requirement: The code host is chosen
jagt SHALL read a deploy's pipeline through the driver `codeHost` names, a project's key over `orchestrator.codeHost` over `github`.
The drivers are `github` (`gh`), `gitlab` (`glab`), `command`, `none` and `replay`. jagt MUST NOT guess the kind from a host name or an
address, and the agent chosen by `agent.cli` selects none. `replay` reads every commit green and re-runs nothing.
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

### Requirement: A project names its login
A `command` project MAY declare `loginCommand`, the argv the `login` verb opens; a `pipelineCommand` or `retryCommand`
exiting 77 SHALL read as signed out. `github` and `gitlab` name their own CLI's login on the host read.

#### Scenario: A command signed out
- **WHEN** `pipelineCommand` exits 77 and `loginCommand` is `["ci-login", "--web"]`
- **THEN** the lost reach names `ci-login --web` as the fix
