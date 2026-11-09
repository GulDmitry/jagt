## REMOVED Requirements

### Requirement: With QA on, QA checks the deploy instead of the session
**Reason**: The role is called `acceptance`.
**Migration**: Replaced by the requirement of the same behaviour under its new name.

## ADDED Requirements

### Requirement: With acceptance on, acceptance checks the deploy instead of the session
With it on, acceptance checks the deploy ([acceptance](../acceptance/spec.md)) and `DeployCheckJob` MUST ask the session nothing.

#### Scenario: Acceptance checks the deploy
- **WHEN** a deploy lands with `orchestrator.roles.acceptance.enabled` on
- **THEN** `deploy` is never sent to the session and acceptance's check runs

## MODIFIED Requirements

### Requirement: Every deploy is checked where it landed
With `roles.acceptance.enabled` off, `DeployCheckJob` SHALL ask the session once per deploy commit, keyed on the task's
turn. Neither another relay nor a restart asks again; what it asks is the brief's `deploy` step. Green MUST report nothing, the task
staying `DEPLOYED`; red is the session's `IN_PROGRESS`, the round running again: reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`
