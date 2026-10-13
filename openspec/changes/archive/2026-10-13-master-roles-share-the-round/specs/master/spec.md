## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per role of the brief SHALL read each round in the task's worktrees, loading no worktree setting.
jagt SHALL write the verdict (`MasterPanel`); each round SHALL cost one heavy read per role, charged as `master`,
the rest after the first, reading its cached round.
