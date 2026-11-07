## ADDED Requirements

### Requirement: A deploy red on its commit stands on a later green before any re-run
A deploy whose commit's pipeline is red on the code SHALL stand on a later green pipeline of the deploy branch containing that commit,
re-running nothing and opening no worktree.

#### Scenario: A later green exists before the re-run
- **WHEN** the pipeline on ABC-42's deploy commit is red on the code, not yet re-run, and a later one on `dev` containing it is green
- **THEN** the deploy stands on that green and the red pipeline's jobs are not re-run
