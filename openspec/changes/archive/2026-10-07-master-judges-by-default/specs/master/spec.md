## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees.
It may not write, commit or push (`HeadlessClaudeRoundReviewer`); jagt SHALL write the verdict (`MasterPanel`).
Each round SHALL cost one heavy read per role, charged to the task as `master`.

#### Scenario: You want a round read before you look
- **WHEN** `master.mode` is `judge`, or unset where the human's words are readable
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

### Requirement: The Master judges by a brief
The Master SHALL judge by `master.brief`, copied from `master-brief.md.dist`, or by that `.dist` where none is named
or copied (`MasterBriefs.file`); a named brief missing SHALL refuse the start.

#### Scenario: No brief copied
- **WHEN** you start jagt without copying a brief
- **THEN** the Master judges by the shipped one
