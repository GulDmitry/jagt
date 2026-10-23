## ADDED Requirements

### Requirement: A running task gains a repository
`add-repo` SHALL cut the task branch's worktree in the project the standing request names exactly as `do` cuts one
(`NewTaskWorktrees`), register it after the session's own, and write every worktree's briefing and sandbox again for
the new set. It SHALL clear the request, hand the session the path under its instruction, and move the task to
`IN_PROGRESS`; a status only a human leaves keeps its own. A cut that fails SHALL unwind, leave the request standing
and put the cause on the card.

#### Scenario: The request is granted
- **WHEN** you press `add-repo` on ABC-42, which asked for `schemaRegistry`
- **THEN** its worktree exists, the session is told where, the request is gone and the task is `IN_PROGRESS`

#### Scenario: The cut fails
- **WHEN** git refuses the worktree
- **THEN** nothing of it is left, the request stands, and the card reads NEEDS INPUT with the cause
