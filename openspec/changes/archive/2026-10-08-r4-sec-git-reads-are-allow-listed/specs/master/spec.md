## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees.
It loads no worktree setting and runs read-only git there, option by option, with `--no-ext-diff --no-textconv`; it calls
the MCP tools `allowed-tools` names alone and writes nothing, its session included. jagt SHALL write the verdict
(`MasterPanel`) and refuse any other call, your allow rules included (`ReadGate`).
Each round SHALL cost one heavy read per role, charged to the task as `master`.

#### Scenario: You want a round read before you look
- **WHEN** `master.mode` is `judge`, or unset where the human's words are readable
- **THEN** each handed-back round is read into the worktree's `master-review.md`; it presses nothing

#### Scenario: The Master is reading a round
- **WHEN** a round is under Master review
- **THEN** the chip says `master review` and nothing asks you; the session was told to end its turn

#### Scenario: A role worth a lighter model
- **WHEN** the brief's role table has a `model` column
- **THEN** that role runs on it; blank keeps `master.model`

#### Scenario: Whose roles a round is read by
- **WHEN** a session hands back a round
- **THEN** its `<self_review>` reads it first, then the Master by the same roles unless its brief names others

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, start a plan that holds and answer a question. It SHALL
deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`); `master.mine` SHALL keep named steps
(`task/MasterRight`) the human's. Closing SHALL stay the human's: the Master's `done` is refused.

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
- **THEN** the Master answers in the session's window and the task resumes; `mine: [answer]` keeps it yours

#### Scenario: Questions keep coming back over an unchanged tree
- **WHEN** they repeat in `master.mode: act`
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
- **THEN** the fix is read anew

#### Scenario: Ready, but its file lists findings
- **WHEN** the verdict is `ready` and `master-review.md` lists findings
- **THEN** it counts as not ready

#### Scenario: The ticket's acceptance check was never run
- **WHEN** a round skipped it
- **THEN** it is not ready, or a question where the check cannot be run

#### Scenario: A round removes something no ticket line asks for
- **WHEN** it removes an endpoint or version unasked
- **THEN** it is a question, not a verdict
