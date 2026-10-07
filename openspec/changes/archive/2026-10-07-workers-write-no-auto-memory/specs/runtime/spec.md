## ADDED Requirements

### Requirement: A task session keeps no auto memory
Every sub-agent worktree's generated `.claude/settings.local.json` SHALL carry `autoMemoryEnabled: false`: it is keyed
by the repository, so every worktree would read what one task wrote.

#### Scenario: A worker learns something about the project
- **WHEN** a task session would remember a fact
- **THEN** nothing reaches the repository's auto memory
