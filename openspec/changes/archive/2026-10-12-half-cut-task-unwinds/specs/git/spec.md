## MODIFIED Requirements

### Requirement: Repositories multiply worktrees, not agents
`task/TaskRepo` SHALL be a list, `repos.get(0)` running the session. Creation is all-or-nothing (`resolveRepos`),
unwinding what was cut wherever it fails; a task's repositories are one scope (`findByWorktree`). A round is merged
as the least finished (`RoundReading.merged`): approved only when all are, the **worst** pipeline, each comment
prefixed with its repository.

#### Scenario: Independent
- **WHEN** two repositories move independently
- **THEN** two tasks, every verb per task

#### Scenario: Joint change
- **WHEN** `do ABC-42 api,web`
- **THEN** one task, one session, a worktree per repository

### Requirement: Done retires checkouts, never in bulk
`done` SHALL end the agent and delete every worktree and checkout the task cut; the branch survives. `prune all`
MUST be refused by name.

#### Scenario: Diff worktrees
- **WHEN** `done <task>` with `jagt-diff-*` worktrees
- **THEN** removed, its project in `jagt.yml` or gone
