## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, start a plan that holds, answer a question and deploy a
`REVIEWED` task once (`MasterDeployJob`); `master.mine` SHALL keep named steps the human's. Closing a task SHALL stay
the human's alone.

#### Scenario: You want it to act for you
- **WHEN** a round is `ready`, or a plan holds, in `master.mode: act`
- **THEN** it is shipped, or the session told to start, for you; `master.mine` keeps steps yours

#### Scenario: The request is green with every thread closed
- **WHEN** a task reaches `REVIEWED` in `master.mode: act`
- **THEN** the Master deploys it; `mine: [deploy]` leaves the press yours

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

### Requirement: Only blocking and wrong stop a round
A plan or round SHALL be ready unless a finding is blocking or wrong; advice SHALL be a `#` line in `master-review.md`.
Its findings SHALL go back to the session, in any mode, and the task to `IN_PROGRESS`.

#### Scenario: Reviewers find only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking or wrong finding
- **THEN** they go back to its session and the task to `IN_PROGRESS`: the fix is read anew

#### Scenario: It writes ready but its file still lists findings
- **WHEN** the verdict is `ready` and `master-review.md` lists findings
- **THEN** it counts as not ready and the findings go back to the session

#### Scenario: The ticket's acceptance check was never run
- **WHEN** a round skipped the ticket's acceptance check
- **THEN** it is not ready, or a question where the check cannot be run

#### Scenario: A round removes something no ticket line asks for
- **WHEN** a round removes something that exists (an endpoint, a version) unasked
- **THEN** it is a question, not a verdict

### Requirement: A verdict rests on proof
The Master SHALL read the ticket (else `task_request.md`) and the diff, or a `PLAN_PENDING` task's `plan.md` in one read
(`MasterPanel.plan`). It SHALL prove by a run any premise its verdict rests on, never from the author.

#### Scenario: The reviewer cannot prove a finding
- **WHEN** a finding cannot be proven
- **THEN** it asks the session to `show:` it, settling nothing

#### Scenario: The session thinks a finding wrong
- **WHEN** the session disagrees with a finding
- **THEN** it writes `disputed:` with evidence in `task_notes.md`; proven, it reopens even a settled decision
