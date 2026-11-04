## ADDED Requirements

### Requirement: A red-pipeline deploy conflict ends on a later green
A deploy conflict held by red pipelines alone SHALL be read while it stands: the newest finished pipeline on each held repository's deploy branch
whose commit contains the deploy commit.
Green on every held repository SHALL end it, the task `DEPLOYED`; anything else SHALL leave it standing, re-running nothing and opening no worktree.
A merge or revert conflict SHALL never be ended by this read.

#### Scenario: A later pipeline containing the deploy commit is green
- **WHEN** ABC-42 is `DEPLOY_CONFLICT` for a red pipeline on its deploy commit and a later finished pipeline on `dev`, whose commit contains it, is green
- **THEN** ABC-42 is `DEPLOYED` and its conflict is gone
- **AND** its session is asked to check the change once, as after any green deploy

#### Scenario: The newest containing pipeline is still red
- **WHEN** the newest finished pipeline on `dev` containing the deploy commit is red, or the host lists none or cannot be read
- **THEN** ABC-42 stays `DEPLOY_CONFLICT` and the next read asks again

#### Scenario: A merge conflict
- **WHEN** ABC-42 is `DEPLOY_CONFLICT` for a merge that conflicts and `dev` has a green pipeline
- **THEN** ABC-42 stays `DEPLOY_CONFLICT` and no pipeline is read
