# master Specification

## Purpose

The Master: an experimental headless reader of each handed-back round that writes a verdict, and in `act` stands
where the human stands.

## Requirements

### Requirement: The Master is a headless run per role, not a window
EXPERIMENTAL (`master.mode`): one headless run per role of the brief SHALL read each round in the task's worktrees,
refused every write, commit and push (`HeadlessClaudeRoundReviewer`); jagt SHALL write the verdict (`MasterPanel`).
Each round SHALL cost one heavy read per role, charged to the task.

#### Scenario: You want a round read before you look
- **WHEN** `master.mode: judge` is set
- **THEN** the Master reads each handed-back round and writes `master-review.md` in the worktree; it presses nothing

#### Scenario: The Master is reading a round
- **WHEN** a round is under Master review
- **THEN** the chip says `master review` and nothing asks you; the session was told to end its turn

#### Scenario: A role worth a lighter model
- **WHEN** the brief's role table has a `model` column
- **THEN** that role runs on it; blank keeps `master.model`

#### Scenario: Whose roles a round is read by
- **WHEN** a session hands back a round
- **THEN** its own `<self_review>` read it first; the Master reads by the same roles, unless its brief names its own table

#### Scenario: What a round costs
- **WHEN** you look at a task's Master spend
- **THEN** `master` shows the total of one heavy read per role

### Requirement: The Master judges by a brief
The Master SHALL judge by `master.brief`, copied from `master-brief.md.dist`; without it jagt SHALL refuse to start.

#### Scenario: No brief configured
- **WHEN** you start jagt with no brief configured
- **THEN** it is refused

### Requirement: The human's own word stands over the Master
Every read SHALL quote the human's own words to the session (`AgentRuntime.humanSaid`), overruling any decision taken
for them; unreadable, they SHALL stop the round
([0010](../../../docs/decisions/0010-the-humans-own-word-stands-over-the-master.md)).

#### Scenario: The human's words cannot be read
- **WHEN** reading what the human said fails
- **THEN** the round stops

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round and answer a question; `master.mine` SHALL keep named
steps the human's. Closing a task SHALL stay the human's alone.

#### Scenario: You want it to act for you
- **WHEN** a round is `ready` in `master.mode: act`
- **THEN** it is shipped for you; `master.mine` keeps steps yours

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

### Requirement: Instructions never land inside your line
A nudge to a session SHALL wait while the human is typing in its window.

#### Scenario: You are typing in the agent's window when instructions arrive
- **WHEN** instructions arrive mid-line
- **THEN** the nudge waits until you send your line; it never lands inside it

### Requirement: Only blocking and wrong stop a round
A round SHALL be ready unless a finding is blocking or wrong; advice SHALL be a `#` line in `master-review.md`. A
not-ready round's findings SHALL go back to the session that wrote the code, in any mode, and the task to
`IN_PROGRESS`.

#### Scenario: Reviewers find only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A round is not ready
- **WHEN** a round has a blocking or wrong finding
- **THEN** they go back to its session and the task to `IN_PROGRESS`: the fix is a new round

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
The Master SHALL read the ticket and the diff, and prove by a run any premise its verdict rests on, never taking it
from the author.

#### Scenario: The reviewer cannot prove a finding
- **WHEN** a finding cannot be proven
- **THEN** it asks the session to `show:` it, settling nothing

#### Scenario: The session thinks a finding wrong
- **WHEN** the session disagrees with a finding
- **THEN** it writes `disputed:` with evidence in `task_notes.md`; proven, it reopens even a settled decision

### Requirement: A settled decision holds across rounds
`master-decisions.md` in the worktree SHALL hold what earlier rounds settled.

#### Scenario: An earlier round settled something
- **WHEN** a later reviewer meets a settled decision
- **THEN** it reopens it only for a blocking reason

### Requirement: Another branch is another task
In `act` the Master SHALL open work needing a branch other than the task's as a task of its own, by a decision line
`do <project> <what to do…> from <branch>` that jagt runs as the human's own `do` (`MasterShip.open`).

#### Scenario: A session needs a second request
- **WHEN** a session asks for a branch of its own in `master.mode: act`
- **THEN** the Master's `do` line opens that task, and the session is told what was opened
