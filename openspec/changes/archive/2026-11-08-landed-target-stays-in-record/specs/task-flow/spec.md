## MODIFIED Requirements

### Requirement: The repository a deploy was handed back on leaves with its closed request
The repository a deploy was handed back on for its merge SHALL leave the record when its request closes, its deploy worktree removed.
The task stays `DEPLOY_CONFLICT` while a repository has landed and repositories after it are unpushed, and the next `deploy` lands them. Where
nothing landed, the task returns to `REVIEWED` and `deploy` is offered again. A repository that landed never leaves the record: `revert` covers it.

#### Scenario: The repository a deploy waits on closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, all landed, and `beta`'s pipeline went red, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta` stays in the task and the deployment's record, and the sweep calls its request gone, naming it
- **AND** the task stays `DEPLOY_CONFLICT`, and `revert` covers `alpha`, `beta` and `gamma`

#### Scenario: The repository a deploy's merge conflicted on closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, `alpha` landed and `beta`'s merge conflicted, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task and the deployment's record, its deploy worktree removed
- **AND** the task stays `DEPLOY_CONFLICT`, `gamma` unpushed, until `deploy` is pressed, which lands `gamma` after `alpha`

#### Scenario: The repository a deploy's merge conflicted on closed, nothing landed
- **WHEN** ABC-42 spans `alpha` and `beta`, neither landed and `beta`'s merge conflicted, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task and the deployment's record, and the task is `REVIEWED` with `deploy` offered again
