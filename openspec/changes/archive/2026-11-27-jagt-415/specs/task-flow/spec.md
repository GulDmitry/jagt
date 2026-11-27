## ADDED Requirements

### Requirement: A read lands only on the generation it was taken at
A fact read from outside SHALL carry the generation it was taken at: the session's start count, the head tip or the requests linked.
The reducer MUST drop one the task has since moved past, state unchanged, and log `read dropped stale` with its kind.

#### Scenario: A sign read before the session started over
- **WHEN** a read of ABC-42's session lands after the session started over
- **THEN** it is dropped, the new session's record unchanged, and the next read takes its place

## MODIFIED Requirements

### Requirement: A closed request takes its repository out of the task
Where a task holds several repositories and the host says one's request was closed unmerged, that repository SHALL leave the task: its checkout is
removed, its branch kept, the session told in one line, and the sweep judges the others in the same pass. A task's only repository and its primary
repository stay, and the sweep calls the request gone, naming it; a request the host says does not exist is called gone the same way and its
repository stays.
#### Scenario: One of three requests closed
- **WHEN** ABC-42 spans `alpha`, `beta` and `gamma`, and the host says `beta`'s request was closed unmerged
- **THEN** `beta` leaves the task, its checkout removed and its branch kept, and the session is told which repository left and why
- **AND** the round is the request of `alpha` and `gamma`; `deploy` and the card never name `beta` again
#### Scenario: The only repository closed
- **WHEN** ABC-42 holds `alpha` alone and the host says its request was closed unmerged
- **THEN** the sweep calls it gone, naming the request
#### Scenario: A request that does not exist
- **WHEN** ABC-42 spans `alpha` and `beta` and the host says `beta`'s request does not exist
- **THEN** the sweep calls it gone, and `beta` stays in the task
#### Scenario: The primary request closed
- **WHEN** ABC-42 spans `alpha` and `beta`, `alpha` primary, and the host says `alpha`'s request was closed unmerged
- **THEN** `alpha` stays in the task with its checkout, and the sweep calls it gone, naming the request
#### Scenario: A repository already landed
- **WHEN** `beta` was deployed, then its request reads closed
- **THEN** `beta` stays in the deployment's record, so `revert` still covers it

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
