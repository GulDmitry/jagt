## ADDED Requirements

### Requirement: Another branch is another task
In `act` the Master SHALL open work needing a branch other than the task's as a task of its own, by a decision line
`do <project> <what to do…> from <branch>` that jagt runs as the human's own `do` (`MasterShip.open`).

#### Scenario: A session needs a second request
- **WHEN** a session asks for a branch of its own in `master.mode: act`
- **THEN** the Master's `do` line opens that task, and the session is told what was opened
