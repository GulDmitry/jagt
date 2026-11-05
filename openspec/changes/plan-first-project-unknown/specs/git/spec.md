## ADDED Requirements

### Requirement: A scratch folder is no repository
A task with no repository SHALL hold no worktree, branch or request: its folder under `scratch/` is outside git for its session
and `ship` and `deploy` are not offered. Its repositories begin with `approve-plan` ([task-start](../task-start/spec.md)).

#### Scenario: A plan-only task
- **WHEN** a task holds only its scratch folder
- **THEN** a push, `ship` and `deploy` are unavailable and `scratch/` is untracked
