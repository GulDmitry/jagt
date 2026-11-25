## MODIFIED Requirements

### Requirement: jagt stops a worktree's container stacks
A session and a command in a task's worktree SHALL carry `COMPOSE_PROJECT_NAME=<repository>-<task>`, so no two tasks share a project.
Removing a worktree SHALL first run `compose down -v` on that project and on every one whose compose file lies under it (`WorktreeContainers.stop`).
Another project SHALL never be touched. `orchestrator.containers` selects `docker-compose` (default) or `none`, which sets no name.
A missing or failing Docker SHALL log a WARN at most and never stop the removal.

#### Scenario: A task is done with a stack running in its worktree
- **WHEN** ABC-42's worktree is removed and project `alpha`'s compose file lies in it
- **THEN** `alpha` is taken down with its volumes before the directory is deleted

#### Scenario: Another project is running
- **WHEN** project `beta`'s compose file lies outside the worktree
- **THEN** `beta` is left running

#### Scenario: Docker is not installed
- **WHEN** the worktree is removed on a machine without Docker
- **THEN** the worktree is removed and nothing is logged above WARN

#### Scenario: Two tasks work on one repository
- **WHEN** ABC-1 and ABC-2 each start a session in their worktree of `shop`
- **THEN** the sessions run as projects `shop-abc-1` and `shop-abc-2`, and closing ABC-1 takes down `shop-abc-1` alone
