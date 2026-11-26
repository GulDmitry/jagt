## MODIFIED Requirements

### Requirement: Nothing rewrites what left the machine
`GitDeploy` (`HEAD:<target>`), `GitWorktrees` (`refs/heads/x:refs/heads/x`) and `WorkspaceBranch` (`HEAD:refs/heads/<branch>`) SHALL push
**one** branch, never `--force` or `-u`;
no `commit --amend` or `reset --hard` onto a pushed commit (sub-agent rule 6). The one exception: a rebase of the task's own
branch onto its request's target. The resume or the session pushes it under `--force-with-lease` when the target moved on.

#### Scenario: Release request
- **WHEN** resuming a `dev` → `main` request **THEN** refused

#### Scenario: Target moved on
- **WHEN** the target gained commits after the branch was cut, or the session ships
- **THEN** it rebases onto the target and lease-pushes before the request is updated, so the request lists only the task's commits
