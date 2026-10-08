## MODIFIED Requirements

### Requirement: Nothing rewrites what left the machine
`pushBranch` SHALL push **one** branch, both-sided refspec, never `--force` or `-u`; no `commit --amend` or
`reset --hard` onto a pushed commit (sub-agent rule 8), the ONE exception the resume rebase. `detachUpstream` unsets
the inherited `origin/<baseBranch>` at creation. Each git call locks its repository (shared checkout). A task named
after a base or deploy branch MUST be refused.

#### Scenario: Wrong push
- **WHEN** a pushed commit turns out wrong
- **THEN** another commit, never a rewrite

#### Scenario: Release request
- **WHEN** resuming a `dev` → `main` request
- **THEN** refused: `dev` is shared
