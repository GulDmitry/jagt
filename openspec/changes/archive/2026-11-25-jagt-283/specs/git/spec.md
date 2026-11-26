## MODIFIED Requirements

### Requirement: Deploy and revert worktrees live under the data folder
A deploy worktree SHALL be `<data>/tasks/<taskId>/worktrees/<project>-deploy`, a revert one `<project>-revert` beside it.
`hasDeployWorktree` asks git who cut it, `mergeIntoAndPush` **refuses** another repository's, only DEPLOY_CONFLICT resumes.
Every press starts the merge over unless the human's own work sits there
(staged, committed or part done; committed only with its base on the target).

#### Scenario: Orphans
- **WHEN** a live task's deploy and revert worktrees stand under the data folder
- **THEN** they are not listed as orphans; those of a task that is gone are

#### Scenario: Blocked path
- **WHEN** `deploy` keeps refusing over the deploy directory
- **THEN** names what is in the way; a sibling's worktree by name
- **AND** editor residue is **deleted** (`requireOnlyEditorResidue`); anything else is left and named (`StaleDeployPathException`)

#### Scenario: Where a deploy cuts
- **WHEN** a deploy or revert conflicts in a project at any path
- **THEN** its worktree stands under `<data>/tasks/<taskId>/worktrees/` and nothing is created beside the repository
