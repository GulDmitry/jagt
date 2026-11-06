## ADDED Requirements

### Requirement: A running task gains a repository
`add-repo` SHALL cut the task branch's worktree in the project the request names, as `do` does (`NewTaskWorktrees`),
or adopt one already on that branch (`GitWorktrees.worktreeOf`). It SHALL register it after the session's own and rewrite
every worktree's briefing and sandbox. It SHALL clear the request, hand the session the path and move the task to `IN_PROGRESS`.
A cut that fails SHALL unwind and leave the request standing, the cause on the card.
`add-repo <task> <project>` SHALL record that project as the request, then run as above. The session need not have asked.
It SHALL be refused, nothing changed, for a project not in `jagt.yml`, or one the task already works in.
It SHALL be refused at `PLAN_PENDING`, `DEPLOYED`, `DEPLOY_CONFLICT`, `REVERTED` and `DONE`.

#### Scenario: The request is granted
- **WHEN** you press `add-repo` on ABC-42, which asked for `schemaRegistry`
- **THEN** its worktree exists, the session is told where, the request is gone and the task is `IN_PROGRESS`
- **AND** a status only a human leaves keeps its own

#### Scenario: The cut fails
- **WHEN** git refuses the worktree
- **THEN** nothing of it is left, the request stands, and the card reads NEEDS INPUT with the cause

#### Scenario: A worktree already stands on the branch
- **WHEN** ABC-42's branch is checked out in a `schemaRegistry` worktree jagt did not cut
- **THEN** `add-repo` registers that worktree, its uncommitted work untouched, and a failure leaves it in place

#### Scenario: The session's repository ships its own memory file
- **WHEN** ABC-42's first repository ships `CLAUDE.md`, so jagt briefed it in `CLAUDE.local.md`
- **THEN** `add-repo` writes that briefing again in `CLAUDE.local.md` and attaches

#### Scenario: The attached repository is trusted as it stands
- **WHEN** `add-repo` attaches `schemaRegistry` to ABC-42
- **THEN** its git config and hooks join the trusted record as they are, and the next `ship` is not refused for them

#### Scenario: The human names the project
- **WHEN** you type `add-repo ABC-42 schemaRegistry` on ABC-42 in `REVIEW_PENDING`, which asked for nothing
- **THEN** its worktree exists, the session is told where, and the task is `IN_PROGRESS`

#### Scenario: A named project is refused
- **WHEN** you name a project not in `jagt.yml`, one ABC-42 already works in, or ABC-42 is `DEPLOYED`
- **THEN** the press is refused with the reason, and the task, its request and its worktrees are as they were

