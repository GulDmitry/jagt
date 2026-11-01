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
A deploy SHALL stand with no pipeline where none can come: the host lists none for `NO_PIPELINE_AFTER` (10 minutes) after the repository's
recorded push, or the repository has no code-host address. A target that stood on the first SHALL be read on while `DEPLOYED`.

#### Scenario: No pipeline will come
- **WHEN** the host lists no pipeline for ABC-42's deploy commit 10 minutes after its recorded push, or its repository's remote is a local path
- **THEN** the deploy stands and its session is asked once; within the 10 minutes the line says the pipeline is not started
- **AND** one INFO says `deploy settled without a pipeline`, naming the cause
- **AND** a pipeline the host lists later is read while ABC-42 is `DEPLOYED`; red on the code makes it `DEPLOY_CONFLICT`

### Requirement: An unreadable pipeline host stops the deploy
A host that does not answer keeps the deploy open. Three reads running that cannot list a pipeline SHALL stop it as `DEPLOY_UNREAD`.
The first read listing every pipeline, run while that stop stands, SHALL end it.

#### Scenario: The deploy pipeline cannot be listed
- **WHEN** three reads running cannot list the pipeline on ABC-42's deploy commit
- **THEN** ABC-42 stops as `DEPLOY_UNREAD`; the first read listing every pipeline ends the stop

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
