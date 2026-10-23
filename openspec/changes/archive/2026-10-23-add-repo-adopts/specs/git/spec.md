## MODIFIED Requirements

### Requirement: A running task gains a repository
`add-repo` SHALL cut the task branch's worktree in the project the request names, as `do` does (`NewTaskWorktrees`).
One already on that branch there it SHALL adopt (`GitWorktrees.worktreeOf`). It SHALL register it after the session's own,
then write every worktree's briefing and sandbox again for the new set and clear the request. It SHALL hand the
session the path under its instruction. It SHALL move the task to `IN_PROGRESS`; a status only a human leaves keeps
its own. A cut that fails SHALL unwind and leave the request standing, the cause on the card.

#### Scenario: The request is granted
- **WHEN** you press `add-repo` on ABC-42, which asked for `schemaRegistry`
- **THEN** its worktree exists, the session is told where, the request is gone and the task is `IN_PROGRESS`

#### Scenario: The cut fails
- **WHEN** git refuses the worktree
- **THEN** nothing of it is left, the request stands, and the card reads NEEDS INPUT with the cause

#### Scenario: A worktree already stands on the branch
- **WHEN** ABC-42's branch is checked out in a `schemaRegistry` worktree jagt did not cut
- **THEN** `add-repo` registers that worktree, its uncommitted work untouched, and a failure leaves it in place
