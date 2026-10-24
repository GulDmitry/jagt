## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees, loading no worktree setting.
jagt SHALL store each answer with the hand-back it asked about (`MasterReview`), `master-review.md` only its rendering;
a round SHALL cost one heavy read per role it applies to, charged as `master`, later roles reading the cached round.

#### Scenario: What a role may call
- **WHEN** a role reads
- **THEN** read-only git, `-C` naming a worktree, plain words, option by option, `--no-ext-diff --no-textconv`
- **AND** only the MCP tools `allowed-tools` names; it writes nothing, its session included
- **AND** jagt refuses any other call, your allow rules included (`ReadGate`)

#### Scenario: A round read before you look
- **WHEN** `master.mode` is `judge`, or unset where the human's words are readable
- **THEN** each round is read into the worktree's `master-review.md`; it presses nothing
- **AND** a verdict answers only the hand-back it was asked about, however late it is written

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

### Requirement: Only blocking, wrong and unproven stop a round
A plan or round SHALL be ready unless a finding is blocking, wrong or unproven, the last a `show:` request. Only
correctness or a stated requirement SHALL stop one; wording, glossary and style SHALL be noise. Advice SHALL be a `#`
line in `master-review.md`. Its findings SHALL go back to the session, in any mode, and the task to `IN_PROGRESS`,
until rounds of repeats reach the human; a finding is the same by file, anchor and kind (`MasterDecisions`).

#### Scenario: Only advice
- **WHEN** findings are only unguarded, noise or an unproven premise
- **THEN** the round is ready

#### Scenario: A bug test red for another reason
- **WHEN** a bug fix's test fails before the fix, but not with the signal the report names
- **THEN** it is wrong, and the round is not ready

#### Scenario: A round is not ready
- **WHEN** a plan or round has a blocking, wrong or unproven finding
- **THEN** the fix is read anew; one with an earlier round's file, anchor and kind goes back `[repeat]`, reworded or not
- **AND** where two rounds running hold only repeats, the second is your question, a restart between them included
- **AND** a verdict `ready` beside findings counts as not ready

#### Scenario: The acceptance check skipped
- **WHEN** a round skipped it
- **THEN** it is not ready, or a question where it cannot be

#### Scenario: An unasked removal
- **WHEN** it removes an endpoint or version unasked
- **THEN** it is a question, not a verdict

### Requirement: A task's work stays in the task
Work a task needs SHALL be done in it, on its own branch, and the Master SHALL open no task: work its answer names as
a new task opens nothing, the session told only you open a task. A repository the task lacks is asked for with
`needsProject` and attached by `add-repo`, never opened as a task.

#### Scenario: The Master's answer holds a `do` line
- **WHEN** in `act` it answers a session naming `do proj drop the old keys from main` as a new task
- **THEN** no task opens; the session does that work on its own branch

### Requirement: A base-only move keeps the verdict
`MasterPanel.review` SHALL keep the last ready verdict while the task's own diff keeps its stored identifier
(`WorktreeInspection.ownDiffId`: the diff since the merge-base, line numbers and index lines left out, hashed). The kept
verdict answers the hand-back in front; none is stored where a role could not read the round or an untracked file is held.

#### Scenario: Only the base moved
- **WHEN** ABC-42 is rebased onto a moved base and handed back with the same own diff
- **THEN** no role reads it; the last ready verdict stands

#### Scenario: A reply or a change of its own
- **WHEN** ABC-42 is handed back with any change of its own, or the same own diff after a not-ready verdict
- **THEN** each role reads the round again, the reply beside it
