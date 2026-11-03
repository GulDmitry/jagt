## MODIFIED Requirements

### Requirement: A closed request takes its repository out of the task
Where a task holds several repositories and the host says one's request was closed unmerged, that repository SHALL
leave the task: its checkout is removed, its branch kept, the session told in one line, and the sweep judges the others
in the same pass. A task's only repository, and a request the host says does not exist, stay gone.
A repository a deploy already landed stays in the deployment's record, so `revert` still covers it.
The one a deploy was handed back on for its pipeline leaves the record too, and the task lands `DEPLOYED` on the others.

#### Scenario: The repository a deploy waits on closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, all landed, and `beta`'s pipeline went red, so the task stands in `DEPLOY_CONFLICT`
- **AND** the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task and the deployment's record, no revert of it is owed, and the task lands `DEPLOYED` on `alpha` and `gamma`
- **AND** the stalled-conflict line ends, and the deploy check goes on for `alpha` and `gamma` as for any deploy
