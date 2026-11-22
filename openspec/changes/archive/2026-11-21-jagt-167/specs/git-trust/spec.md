## MODIFIED Requirements

### Requirement: Git with the human's credentials waits for trust
`ship`, `deploy` and `revert` (not offered while the press's read of git differs) and a Master read (`ChargedReviews`) SHALL compare the record with
what git obeys now. A difference refuses, naming each key and file, and parks the task: `NEEDS YOU` on its card,
`trust` its only move among those three. A state that cannot be read refuses without parking, with `git state of <task> could not be read: <why>`.

#### Scenario: A session sets a program
- **WHEN** a session runs `git config core.fsmonitor /tmp/x` and you press Deploy
- **THEN** refused: `core.fsmonitor in <repo>/.git/config added`, nothing merged, the task parked

#### Scenario: The Master reads a changed task
- **WHEN** the Master's read meets a changed state
- **THEN** nothing is read and the round's question goes to you

#### Scenario: A push writes tracking
- **WHEN** a session pushes with `-u`
- **THEN** nothing parks

#### Scenario: Git is read on a press only
- **WHEN** the board is projected, or a verb git does not gate is pressed
- **THEN** git's state is not read
