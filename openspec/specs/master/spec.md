# master Specification

## Purpose

The Master: a headless reader writing a verdict on each handed-back round; in `act` it stands where the
human stands.

## Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees, loading no worktree setting.
jagt SHALL write the verdict (`MasterPanel`); each round SHALL cost one heavy read per role, charged as `master`.

#### Scenario: What a role may call
- **WHEN** a role reads
- **THEN** read-only git, `-C` naming a worktree, plain words, option by option, `--no-ext-diff --no-textconv`
- **AND** only the MCP tools `allowed-tools` names; it writes nothing, its session included
- **AND** jagt refuses any other call, your allow rules included (`ReadGate`)

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

### Requirement: The Master judges by a brief
The Master SHALL judge by `master.brief`, copied from `master-brief.md.dist`, or by that `.dist` where none is named
or copied (`MasterBriefs.file`); a named brief missing SHALL refuse the start.

#### Scenario: No brief copied
- **WHEN** you start jagt without copying a brief
- **THEN** the Master judges by the shipped one

### Requirement: The human's own word stands over the Master
Every read SHALL quote the human's own words to the session (`AgentRuntime.humanSaid`), overruling any decision taken
for them; unreadable, they SHALL stop the round
([0010](../../../docs/decisions/0010-the-humans-own-word-stands-over-the-master.md)).

#### Scenario: The human's words cannot be read
- **WHEN** reading what the human said fails
- **THEN** the round stops

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

### Requirement: Instructions never land inside your line
A nudge to a session SHALL wait while the human is typing in its window.

#### Scenario: You are typing when instructions arrive
- **WHEN** instructions arrive mid-line
- **THEN** the nudge waits until you send your line

### Requirement: Only blocking, wrong and unproven stop a round
A plan or round SHALL be ready unless a finding is blocking, wrong or unproven, the last a `show:` request; advice
SHALL be a `#` line in `master-review.md`. Its findings SHALL go back to the session, in any mode, and the task to
`IN_PROGRESS`.

#### Scenario: Reviewers find only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking, wrong or unproven finding
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

### Requirement: A verdict rests on proof
The Master SHALL read the ticket (else `task_request.md`) and the diff, or a `PLAN_PENDING` task's `plan.md` in one read
(`MasterPanel.plan`). It SHALL prove each premise by a run, never from the author; an unread ticket proves nothing.

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
In `act` work needing another branch SHALL be its own task, opened by the Master's line
`do <project> <what to do…> from <branch>` (`MasterShip.open`), the session told what opened; a `from`, `into` or
`onto` the deploy branch SHALL open nothing.

#### Scenario: A session asks for its work on the deploy branch
- **WHEN** its `do` line says `into dev`
- **THEN** nothing opens; that merge is the task's own `deploy`, once `REVIEWED`

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following
`jagt-master.log`, one line per Master step (`MasterFeedWindow`); closed, it SHALL come back within seconds.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys or opens a task
- **THEN** the `master` window shows it with the task, and an answer with its question and decision
