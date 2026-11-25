## ADDED Requirements

### Requirement: A project's companions join every task started on it
A project's `companions` SHALL name projects every task started or placed on it also works in.
Each is cut after the named repositories; its own companions are not followed.
Startup MUST refuse a companion that is not a project, naming the key.
#### Scenario: A companion joins
- **WHEN** `api` names companion `specs` and ABC-42 starts on `api`
- **THEN** ABC-42 holds worktrees of `api` and `specs`, its session in `api`, and ships one request per repository
#### Scenario: A companion that is not a project
- **WHEN** `api` names companion `specs` and no project `specs` is configured
- **THEN** startup refuses, naming `orchestrator.projects.api.companions`
