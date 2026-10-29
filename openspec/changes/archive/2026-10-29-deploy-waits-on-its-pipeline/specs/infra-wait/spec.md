## ADDED Requirements

### Requirement: A deploy stands on the pipeline jagt reads on its commit
A deploy SHALL stand only once the host's pipeline on each repository's `deployCommit` is green. `DeployCheckJob` reads it every minute through the host's own CLI, not a model, while the deploy has not been asked about. A pipeline running, not started, unknown or unreadable keeps the deploy open, and the task's line says `waiting on its pipeline`. A pipeline red on infrastructure SHALL be retried and counted as a request's is, and stops the task as `INFRA` after the last retry.

#### Scenario: The deploy pipeline is not green yet
- **WHEN** ABC-42 is `DEPLOYED` and the pipeline on its deploy commit is running, not started or cannot be read
- **THEN** its session is not asked and the task stays `DEPLOYED`
- **AND** its line reads `deployed to dev — waiting on its pipeline: proj 01234567: pipeline running`

#### Scenario: The deploy pipeline goes green
- **WHEN** the pipeline on every deploy commit of ABC-42 reads green
- **THEN** its session is asked once, and the host is read no more for that deploy

#### Scenario: The deploy pipeline dies on infrastructure
- **WHEN** the pipeline on ABC-42's deploy commit went red with every failed job on infrastructure
- **THEN** the deploy stays open and its line names the retry
- **AND** after the last retry the task stops as `INFRA`

## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out

#### Scenario: A task past its review
- **WHEN** ABC-42 is `DEPLOYED`, `DEPLOY_CONFLICT` or `REVERTED` and its open request's pipeline dies on infrastructure
- **THEN** no retry is armed or sent and no `INFRA` stop is set
- **AND** a read of that request leaves a retry armed for the deploy pipeline standing
