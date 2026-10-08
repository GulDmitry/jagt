## MODIFIED Requirements

### Requirement: Nothing rewrites what left the machine
`GitDeploy` (`HEAD:<target>`) and `GitWorktrees` (`refs/heads/x:refs/heads/x`) SHALL push **one** branch, never
`--force` or `-u`; no `commit --amend` or `reset --hard` onto a pushed commit (sub-agent rule 8). The ONE exception:
the resume rebase's `--force-with-lease` of the task's own branch. `detachUpstream` unsets the inherited
`origin/<baseBranch>` at creation. Each git call locks its repository (shared checkout). A task named after a base or
deploy branch MUST be refused.

#### Scenario: Wrong push
- **WHEN** a pushed commit turns out wrong
- **THEN** another commit, never a rewrite

#### Scenario: Release request
- **WHEN** resuming a `dev` → `main` request
- **THEN** refused: `dev` is shared
