## MODIFIED Requirements

### Requirement: jagt stops a worktree's container stacks
A compose command SHALL run as `<repository>-<task>` of its own worktree, wherever its session started.
Removing a worktree SHALL first run `compose down -v` on that project and each whose compose file lies under it (`WorktreeContainers.stop`).
Another project SHALL never be touched. `orchestrator.containers` is `docker-compose` (default) or `none`, which names none.
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

#### Scenario: A session runs compose in its second repository
- **WHEN** a session started in ABC-42's worktree of `a` runs a compose command in its worktree of `b`
- **THEN** the project is `b-abc-42`
