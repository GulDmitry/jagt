# master Specification

## Purpose

The Master: a headless reader judging each handed-back round; in `act` it stands where the human stands.

## Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees, loading no worktree setting.
jagt SHALL write the verdict (`MasterPanel`); each round SHALL cost one heavy read per role it applies to, charged as
`master`, the rest after the first, reading its cached round.

#### Scenario: What a role may call
- **WHEN** a role reads
- **THEN** read-only git, `-C` naming a worktree, plain words, option by option, `--no-ext-diff --no-textconv`
- **AND** only the MCP tools `allowed-tools` names; it writes nothing, its session included
- **AND** jagt refuses any other call, your allow rules included (`ReadGate`)

#### Scenario: A round read before you look
- **WHEN** `master.mode` is `judge`, or unset where the human's words are readable
- **THEN** each round is read into the worktree's `master-review.md`; it presses nothing

#### Scenario: The Master is reading a round
- **WHEN** it is under review
- **THEN** the chip says `master review` and nothing asks you; the session was told to end its turn

#### Scenario: A role worth a lighter model
- **WHEN** a role's row names a `model`
- **THEN** it runs on it; blank keeps `master.model`

#### Scenario: A role the diff does not touch
- **WHEN** the diff touches none of a role's `applies` globs
- **THEN** a `# skipped` line replaces its read; unread paths, or no role left, read every role

#### Scenario: Whose roles read a round
- **WHEN** a session hands back a round
- **THEN** its `<self_review>` reads it first, then the Master by the same roles unless its brief names others

### Requirement: The Master judges by a brief
The Master SHALL judge by `master.brief`, copied from `master-brief.md.dist`, else by that `.dist`
(`MasterBriefs.file`); a named brief missing SHALL refuse the start.

#### Scenario: No brief copied
- **WHEN** no brief is copied
- **THEN** the shipped one judges

### Requirement: The human's own word stands over the Master
Every read SHALL quote the human's own words to the session (`AgentRuntime.humanSaid`), overruling any decision taken
for them ([0010](../../../docs/decisions/0010-the-humans-own-word-stands-over-the-master.md)).

#### Scenario: The human's words cannot be read
- **WHEN** reading them fails
- **THEN** the round stops

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and
answer a question. A permission stays yours. It SHALL deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`);
`master.mine` SHALL keep named steps (`task/MasterRight`) the human's, and a project's `humanPaths` a task's ship
and deploy where it changes a path they name (`HumanPaths`). Closing SHALL stay the human's: the Master's `done` is refused.

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

#### Scenario: A session asks for a permission
- **WHEN** it reports `outcome=permission`, stopped on a call its CLI denied
- **THEN** the Master never answers: no model grants a right, so the question is yours

#### Scenario: Questions repeat over an unchanged tree
- **WHEN** they repeat
- **THEN** after three, a fresh session and an answer that changes the worktrees; the ninth is a runaway, yours

#### Scenario: It is unsure what the ticket asks
- **WHEN** the ticket could mean "switch" or "add beside"
- **THEN** it decides as you would, sent back as a finding

### Requirement: Instructions never land inside your line
A nudge to a session SHALL wait while the human is typing in its window.

#### Scenario: You are typing
- **WHEN** instructions arrive mid-line
- **THEN** they wait for your line

### Requirement: Only blocking, wrong and unproven stop a round
A plan or round SHALL be ready unless a finding is blocking, wrong or unproven, the last a `show:` request. Only
correctness or a stated requirement SHALL stop one; wording, glossary and style SHALL be noise. Advice SHALL be a `#`
line in `master-review.md`. Its findings SHALL go back to the session, in any mode, and the task to
`IN_PROGRESS`, until rounds of repeats reach the human (`FindingMatch`).

#### Scenario: Only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A bug test red for another reason
- **WHEN** a bug fix's test fails before the fix, but not with the signal the report names
- **THEN** it is wrong, and the round is not ready

#### Scenario: A glossary nit
- **WHEN** a reader finds only a term used against the glossary
- **THEN** it is noise and the round is ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking, wrong or unproven finding
- **THEN** the fix is read anew; one an earlier round made on the same file goes back `[repeat]`
- **AND** where two rounds running hold only repeats, the second is your question

#### Scenario: Ready beside findings
- **WHEN** the verdict is `ready` beside findings
- **THEN** it counts as not ready

#### Scenario: The acceptance check skipped
- **WHEN** a round skipped it
- **THEN** it is not ready, or a question where it cannot be

#### Scenario: An unasked removal
- **WHEN** it removes an endpoint or version unasked
- **THEN** it is a question, not a verdict

### Requirement: A verdict rests on proof
The Master SHALL read the ticket (else `task_request.md`) and the diff, or a `PLAN_PENDING` task's `plan.md` in one read
(`MasterPanel.plan`). It SHALL prove each premise by a run, never from the author; an unread ticket proves nothing.

#### Scenario: An unprovable finding
- **WHEN** it cannot prove one
- **THEN** it asks the session to `show:` it, settling nothing

#### Scenario: The session disputes a finding
- **WHEN** it disagrees
- **THEN** it writes `disputed:` with evidence in `task_notes.md`; proven, it reopens even a settled decision

### Requirement: A settled decision holds across rounds
`master-decisions.md` in the worktree SHALL hold what earlier rounds settled.

#### Scenario: An earlier round settled something
- **WHEN** a later reviewer meets it
- **THEN** only a blocking reason reopens it

### Requirement: Another branch is another task
In `act` work needing another branch SHALL be its own task, opened by the Master's line
`do <project> <what to do…> from <branch>` (`MasterShip.open`), the session told what opened; a `from`, `into` or
`onto` the deploy branch, or answering the reviewer's own question, SHALL open nothing.

#### Scenario: Work asked onto the deploy branch
- **WHEN** its `do` line says `into dev`
- **THEN** nothing opens; that merge is the task's own `deploy`, once `REVIEWED`

#### Scenario: The Master answers its own question
- **WHEN** a `do` line answers the reviewer's own question
- **THEN** nothing opens; what it found unasked is yours

### Requirement: A disagreement offers a lesson, never applies one
The `master` report SHALL offer a `master-lesson` run for the latest task you reverted after a pass or deployed after
a fail (`MasterRecord`); it changes no brief.

#### Scenario: You reverted what it passed
- **WHEN** ABC-42 passed and you reverted it
- **THEN** `master` shows a `lesson:` line: what to paste into `/master-lesson`

### Requirement: A base-only move keeps the verdict
`MasterPanel.review` SHALL keep the last verdict, restamped, when it is ready and the task's own diff has the
identifier stored in it (`WorktreeInspection.ownDiffId`: the diff since the merge-base, line numbers and index lines
left out, hashed).
A verdict SHALL store no identifier when a role could not read the round, and a task holding an untracked file has none.

#### Scenario: Only the base moved
- **WHEN** ABC-42 is rebased onto a moved base and handed back with the same own diff
- **THEN** no role reads it; the last ready verdict stands

#### Scenario: The session replied to a not-ready round
- **WHEN** ABC-42 is handed back with the same own diff after a not-ready verdict
- **THEN** each role reads the round again, the reply beside it

#### Scenario: The task's own diff changed
- **WHEN** ABC-42 is handed back with any change of its own
- **THEN** each role reads the round as before
