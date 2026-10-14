## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees, loading no worktree setting.
jagt SHALL write the verdict (`MasterPanel`); each round SHALL cost one heavy read per role it applies to, charged as
`master`, the rest after the first, reading its cached round.

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

#### Scenario: A role the diff does not touch
- **WHEN** the diff touches no path the globs of a role's `applies` column name
- **THEN** a `# skipped` line replaces its read; unread paths, or no role left, read every role

#### Scenario: Whose roles a round is read by
- **WHEN** a session hands back a round
- **THEN** its `<self_review>` reads it first, then the Master by the same roles unless its brief names others
