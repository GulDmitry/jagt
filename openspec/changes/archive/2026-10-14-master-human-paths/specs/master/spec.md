## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, start a plan that holds and answer a question. It SHALL
deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`); `master.mine` SHALL keep named steps
(`task/MasterRight`) the human's, and a project's `humanPaths` the ship and deploy of a task changing a path they name
(`HumanPaths`). Closing SHALL stay the human's: the Master's `done` is refused.

#### Scenario: It acts for you
- **WHEN** a round is `ready`, or a plan holds
- **THEN** it is shipped, or the session started

#### Scenario: A green request, every thread closed
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it; `mine: [deploy, revert]` leaves the press yours

#### Scenario: A ready round holds nothing to ship
- **WHEN** it has nothing to ship
- **THEN** it presses nothing; the task waits for you

#### Scenario: A task changes a path kept for you
- **WHEN** a changed path matches `humanPaths: ["**/auth/**"]`, or the paths cannot be read
- **THEN** it ships and deploys nothing; the `master` window names the path

#### Scenario: A session stops to ask
- **WHEN** it asks a question
- **THEN** the Master answers in its window, the task resumes; `mine: [answer]` keeps it yours

#### Scenario: Questions repeat over an unchanged tree
- **WHEN** they repeat
- **THEN** after three, a fresh session and an answer that changes the worktrees; the ninth is a runaway, yours

#### Scenario: It is unsure what the ticket asks
- **WHEN** the ticket could mean "switch" or "add beside"
- **THEN** in `act` it decides as you would, sent back as a finding
- **AND** in `judge`, or where `mine` keeps `answer`, it asks you
