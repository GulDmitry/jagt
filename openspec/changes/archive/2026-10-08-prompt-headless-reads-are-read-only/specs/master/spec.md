## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees.
It may run read-only git alone, writing nothing (`HeadlessClaudeRoundReviewer`); jagt SHALL write the verdict
(`MasterPanel`).
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
- **THEN** its own `<self_review>` read it first; the Master reads by the same roles, unless its brief names its own table
