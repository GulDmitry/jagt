## ADDED Requirements

### Requirement: jagt stops a worktree's container stacks
Removing a worktree SHALL first take down every Compose project whose compose file lies under it
(`port/WorktreeContainers`, `DockerComposeContainers.stop`, `docker compose -p <project> down -v --remove-orphans`). A project outside the
worktree SHALL never be touched. `orchestrator.containers` selects the driver: `docker-compose` (default) or `none`.
A missing or failing Docker SHALL log at most a WARN and never stop the removal.

#### Scenario: A task is done with a stack running in its worktree
- **WHEN** ABC-42's worktree is removed and project `alpha`'s compose file lies in it
- **THEN** `alpha` is taken down with its volumes before the directory is deleted

#### Scenario: Another project is running
- **WHEN** project `beta`'s compose file lies outside the worktree
- **THEN** `beta` is left running

#### Scenario: Docker is not installed
- **WHEN** the worktree is removed on a machine without Docker
- **THEN** the worktree is removed and nothing is logged above WARN
