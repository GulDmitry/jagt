## MODIFIED Requirements

### Requirement: Another branch is another task
In `act` work needing another branch SHALL be its own task, opened by the Master's line
`do <project> <what to do…> from <branch>` (`MasterShip.open`), the session told what opened; a `from`, `into` or
`onto` the deploy branch, or answering the reviewer's own question, SHALL open nothing.

#### Scenario: The Master answers its own question
- **WHEN** a `do` line answers the reviewer's own question
- **THEN** nothing opens; what it found unasked is yours
