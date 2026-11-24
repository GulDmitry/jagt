## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per brief role SHALL read each round in the worktrees and present `agent.sandbox.allowWrite` dirs, loading no worktree setting.
jagt SHALL store each answer with the hand-back it asked about (`MasterReview`), `master-review.md` only its rendering;
a round SHALL cost one heavy read per role it applies to, charged as `master`, later roles reading the cached round.

#### Scenario: What a role may call
- **WHEN** a role reads
- **THEN** read-only git, `-C` naming a worktree, plain words, option by option, `--no-ext-diff --no-textconv`
- **AND** only the MCP tools `allowed-tools` names; it writes nothing, its session included
- **AND** jagt refuses any other call, your allow rules included (`ReadGate`)

#### Scenario: A round read before you look
- **WHEN** `roles.master.mode` is `judge`
- **THEN** each round is read into the worktree's `master-review.md`; it presses nothing
- **AND** a verdict answers only the hand-back it was asked about, however late it is written; a file jagt did not write answers none
- **WHEN** it is under review
- **THEN** the chip says `master review` and nothing asks you; the session was told to end its turn

#### Scenario: Which roles read, and on what model
- **WHEN** a role's row names a `model`
- **THEN** it runs on it; blank keeps `roles.master.model`
- **WHEN** the diff touches none of a role's `applies` globs, only paths its `!` globs name, or its `unless` names a role that is on
- **THEN** a `# skipped` line replaces its read; unread paths, no role left, or no code host, read every role
- **WHEN** a session hands back a round
- **THEN** its `<self_review>` reads it first, then the Master by the same roles unless its brief names others
