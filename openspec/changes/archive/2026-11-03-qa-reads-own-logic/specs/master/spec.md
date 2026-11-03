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
- **WHEN** `roles.master.mode` is `judge`, or unset where the human's words are readable
- **THEN** each round is read into the worktree's `master-review.md`; it presses nothing
- **AND** a verdict answers only the hand-back it was asked about, however late it is written; a file jagt did not write answers none

#### Scenario: The Master is reading a round
- **WHEN** it is under review
- **THEN** the chip says `master review` and nothing asks you; the session was told to end its turn

#### Scenario: A role worth a lighter model
- **WHEN** a role's row names a `model`
- **THEN** it runs on it; blank keeps `roles.master.model`

#### Scenario: A role the diff does not touch
- **WHEN** the diff touches none of a role's `applies` globs, or only paths its `!` globs name
- **THEN** a `# skipped` line replaces its read; unread paths, or no role left, read every role

#### Scenario: Whose roles read a round
- **WHEN** a session hands back a round
- **THEN** its `<self_review>` reads it first, then the Master by the same roles unless its brief names others

