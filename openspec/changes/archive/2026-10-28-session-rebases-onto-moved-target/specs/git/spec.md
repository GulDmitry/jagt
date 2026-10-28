## MODIFIED Requirements

### Requirement: Nothing rewrites what left the machine
`GitDeploy` (`HEAD:<target>`) and `GitWorktrees` (`refs/heads/x:refs/heads/x`) SHALL push **one** branch, never
`--force` or `-u`; no `commit --amend` or `reset --hard` onto a pushed commit (sub-agent rule 6). The one exception is a rebase
of the task's own branch onto its request's target, pushed under `--force-with-lease` by the resume or by the session
whenever the target moved on. `detachUpstream` unsets the inherited `origin/<baseBranch>` at
creation. Each git call locks its repository (shared checkout). A task named after a base or
deploy branch MUST be refused.

#### Scenario: Release request
- **WHEN** resuming a `dev` → `main` request
- **THEN** refused

#### Scenario: Target moved on
- **WHEN** the target gained commits after the branch was cut, or the session ships
- **THEN** it rebases onto the target and lease-pushes before the request is updated, so the request lists only the task's commits
