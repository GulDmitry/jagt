## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, start a plan that holds and answer a question. It SHALL
deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`); `master.mine` SHALL keep named steps the human's. Closing
SHALL stay the human's: the Master's `done` is refused.

#### Scenario: You want it to act for you
- **WHEN** a round is `ready`, or a plan holds, in `master.mode: act`
- **THEN** it is shipped, or the session started

#### Scenario: The request is green with every thread closed
- **WHEN** a task reaches `REVIEWED` or `APPROVED` in `master.mode: act`
- **THEN** the Master deploys it; `mine: [deploy, revert]` leaves the press yours

#### Scenario: A ready round holds nothing to ship
- **WHEN** a `ready` round has nothing to ship in `master.mode: act`
- **THEN** it presses nothing and the task waits for you

#### Scenario: A session stops to ask
- **WHEN** a session asks a question in `master.mode: act`
- **THEN** the Master answers it in the session's window and the task goes back to work; `mine: [answer]` keeps it yours

#### Scenario: Questions keep coming back over an unchanged tree
- **WHEN** questions repeat over an unchanged tree in `master.mode: act`
- **THEN** after three, a fresh session and an answer that changes the worktrees; the ninth is a runaway, yours

#### Scenario: It is unsure what the ticket asks
- **WHEN** the ticket could mean "switch" or "add beside"
- **THEN** in `act` it decides as you would and sends the decision back as a finding
- **AND** a question reaches you in `judge`, or where `mine` keeps `answer`
