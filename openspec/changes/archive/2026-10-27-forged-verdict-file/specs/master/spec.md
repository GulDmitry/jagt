## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees, loading no worktree setting.
jagt SHALL store each answer with the hand-back it asked about (`MasterReview`), `master-review.md` only its rendering;
a round SHALL cost one heavy read per role it applies to, charged as `master`, later roles reading the cached round.

#### Scenario: A round read before you look
- **WHEN** `master.mode` is `judge`, or unset where the human's words are readable
- **THEN** each round is read into the worktree's `master-review.md`; it presses nothing
- **AND** a verdict answers only the hand-back it was asked about, however late it is written; a file jagt did not write answers none
