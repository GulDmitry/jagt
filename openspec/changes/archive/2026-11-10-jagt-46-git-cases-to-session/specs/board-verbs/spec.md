## ADDED Requirements

### Requirement: A git case the session can work has a verb
`hand-off` SHALL say the task's standing git cases to its session through `AgentSessions.say`, one block per repository: the task branch, the
shas involved, the worktree and the command to run (a deploy push the host rejected, a branch diverged from its remote, a conflict waiting in a deploy
or revert worktree). It is typed, with no button, offered from every started status, and the Master holds it in every mode. A refusal never tells the
human to run git: it names the facts and `hand-off`.
#### Scenario: A deploy push the host rejected
- **WHEN** ABC-42's deploy push was rejected because the deploy branch moved and the human types `hand-off ABC-42`
- **THEN** its session is told the deploy worktree, the branch and `git merge origin/<deploy branch>`
- **AND** the Master in `judge` or `act` may press it too

#### Scenario: Nothing to hand
- **WHEN** `hand-off ABC-42` and no repository of it stands in a git case
- **THEN** it is refused, saying so, and the session is told nothing
