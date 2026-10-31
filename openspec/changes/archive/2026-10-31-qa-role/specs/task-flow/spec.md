## MODIFIED Requirements

### Requirement: Every deploy is checked where it landed
With `orchestrator.qa.enabled` off, `DeployCheckJob` SHALL ask the session once per deploy commit, keyed on the task's turn, so neither another relay nor a restart asks again; what it
asks is the brief's `deploy` step. Green MUST report nothing, the task staying `DEPLOYED`; red is the session's `IN_PROGRESS`, the round running
again: reviewed, deployed, checked. It SHALL ask only once the pipeline on each deploy commit is green
([infra-wait](../infra-wait/spec.md#requirement-a-deploy-stands-on-the-pipeline-jagt-reads-on-its-commit)).
With it on, QA checks the deploy ([qa](../qa/spec.md)) and `DeployCheckJob` MUST ask the session nothing; never both on one deploy.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`

#### Scenario: QA checks the deploy
- **WHEN** a deploy lands with `orchestrator.qa.enabled` on
- **THEN** `deploy` is never sent to the session and QA's check runs
