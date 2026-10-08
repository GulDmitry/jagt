## MODIFIED Requirements

### Requirement: Another branch is another task
In `act` work needing another branch SHALL be its own task, opened by the Master's line
`do <project> <what to do…> from <branch>` (`MasterShip.open`), the session told what opened; a `from`, `into` or
`onto` the deploy branch SHALL open nothing.

#### Scenario: A session asks for its work on the deploy branch
- **WHEN** its `do` line says `into dev`
- **THEN** nothing opens; that merge is the task's own `deploy`, once `REVIEWED`
