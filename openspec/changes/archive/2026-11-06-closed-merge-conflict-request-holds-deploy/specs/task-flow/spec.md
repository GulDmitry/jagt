## MODIFIED Requirements

### Requirement: A closed request takes its repository out of the task
Where a task holds several repositories and the host says one's request was closed unmerged, that repository SHALL
leave the task: its checkout is removed, its branch kept, the session told in one line, and the sweep judges the others
in the same pass. A task's only repository, and a request the host says does not exist, stay gone.
The one a deploy was handed back on for its merge leaves the record and its deploy worktree is removed; the task stays
`DEPLOY_CONFLICT` while repositories after it are unpushed, and the next `deploy` lands them.

#### Scenario: The repository a deploy's merge conflicted on closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, `alpha` landed and `beta`'s merge conflicted, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task and the deployment's record, its deploy worktree removed
- **AND** the task stays `DEPLOY_CONFLICT`, `gamma` unpushed, until `deploy` is pressed, which lands `gamma` after `alpha`
