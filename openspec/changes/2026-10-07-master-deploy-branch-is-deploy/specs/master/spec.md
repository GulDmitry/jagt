## MODIFIED Requirements

### Requirement: Another branch is another task
In `act` work needing another branch SHALL be a task of its own, opened by the Master's line
`do <project> <what to do…> from <branch>` (`MasterShip.open`), the session told what opened; one naming the deploy
branch SHALL open nothing.

#### Scenario: A session asks for its work on the deploy branch
- **WHEN** the Master's `do` line names the deploy branch
- **THEN** nothing opens; that merge is the task's own `deploy`, once `REVIEWED`
