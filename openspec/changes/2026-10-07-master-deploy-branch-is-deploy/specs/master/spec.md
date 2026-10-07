## MODIFIED Requirements

### Requirement: Another branch is another task
In `act` work needing another branch SHALL be a task of its own, opened by the Master's decision line
`do <project> <what to do…> from <branch>` run as the human's `do` (`MasterShip.open`). A line naming the deploy
branch SHALL open nothing: that merge is the task's own `deploy`.

#### Scenario: A session needs a second request
- **WHEN** a session asks for a branch of its own in `master.mode: act`
- **THEN** the Master's `do` line opens that task, and the session is told what was opened

#### Scenario: A session asks for its work on the deploy branch
- **WHEN** the Master's `do` line names the deploy branch
- **THEN** nothing opens; the task waits for its request to go green, then `deploy`
