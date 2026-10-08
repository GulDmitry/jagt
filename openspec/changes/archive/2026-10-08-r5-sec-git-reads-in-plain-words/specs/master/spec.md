## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees.
It loads no worktree setting and runs read-only git there in plain words, option by option, with `--no-ext-diff --no-textconv`;
it calls the MCP tools `allowed-tools` names alone and writes nothing, its session included. jagt SHALL write the verdict
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
