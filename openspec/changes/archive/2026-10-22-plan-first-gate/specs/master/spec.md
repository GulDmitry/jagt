## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and answer a question. It SHALL
deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`); `master.mine` SHALL keep named steps
(`task/MasterRight`) the human's, and a project's `humanPaths` a task's ship and deploy where it changes a path they name
(`HumanPaths`). Closing SHALL stay the human's: the Master's `done` is refused.

#### Scenario: Green with every thread closed
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it; `mine: [deploy, revert]` leaves the press yours

#### Scenario: A ready round holds nothing to ship
- **WHEN** it has nothing to ship
- **THEN** it presses nothing; the task waits for you

#### Scenario: A path kept for you
- **WHEN** `humanPaths: ["**/auth/**"]` names a changed path, or the paths are unread
- **THEN** it presses neither; the `master` window names the path

#### Scenario: A session stops to ask
- **WHEN** a session asks a question
- **THEN** the Master answers in its window, the task resumes; `mine: [answer]` keeps it yours

#### Scenario: Questions repeat over an unchanged tree
- **WHEN** they repeat
- **THEN** after three, a fresh session and an answer that changes the worktrees; the ninth is a runaway, yours

#### Scenario: It is unsure what the ticket asks
- **WHEN** the ticket could mean "switch" or "add beside"
- **THEN** it decides as you would, sent back as a finding
