## ADDED Requirements

### Requirement: A closed request takes its repository out of the task
Where a task holds several repositories and the host says one's request was closed unmerged, that repository SHALL
leave the task: its checkout is removed, its branch kept, the session told in one line, and the sweep judges the others
in the same pass. A task's only repository, and a request the host says does not exist, stay gone.
A repository a deploy already landed stays in the deployment's record, so `revert` still covers it.

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

#### Scenario: A repository already landed
- **WHEN** `beta` was deployed, then its request reads closed
- **THEN** `beta` stays in the deployment's record and `revert` still takes it out
