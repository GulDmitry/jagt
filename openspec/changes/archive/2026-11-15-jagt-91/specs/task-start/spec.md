## ADDED Requirements

### Requirement: A project's setup command runs in every new worktree
A project's `setupCommand`, an argv list, SHALL run once in every new worktree of that project.
It runs after `worktree.copyGlobs` and before the session starts, with `{branch}` and `{worktree}` filled,
within `setupMinutes` (20 where unset). A non-zero exit or the limit
MUST fail the start naming the project, the exit and the output's tail; every cut is unwound and no session starts.
Unset, nothing runs.
#### Scenario: A setup command
- **WHEN** a project names `setupCommand` and a task starts in it
- **THEN** it runs once in the new worktree with the branch and the worktree path filled, before the session starts
#### Scenario: A setup that fails
- **WHEN** `setupCommand` exits non-zero or passes `setupMinutes`
- **THEN** the start fails naming the project, the exit and the output's tail, and no session starts
#### Scenario: No setup command
- **WHEN** a project names no `setupCommand`
- **THEN** a task starts as before
