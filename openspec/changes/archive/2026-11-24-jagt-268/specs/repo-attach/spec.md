## ADDED Requirements

### Requirement: A repository holding no work leaves the task
Where a task's repository has a request, each repository with no request that holds no work SHALL leave the task, the
primary one included (`RepoDeparture.leaveWorkless`); one the approved plan marks unaffected leaves the same way.
A primary that leaves moves the task's home to the repository that comes first.
A primary SHALL stay while an agent runs in its checkout. A task's only repository SHALL stay.

#### Scenario: The primary holds no work
- **WHEN** ABC-42 works in `api` and `web`, only `web` has a request, `api` holds no work and no agent runs
- **THEN** `api` leaves ABC-42 and its next session starts in `web`

#### Scenario: An agent runs in the primary
- **WHEN** an agent runs in the checkout of `api`, the primary one holding no work
- **THEN** `api` stays until a later read finds no agent in it

#### Scenario: The only repository holds no work
- **WHEN** ABC-42 works in `api` alone and it holds no work
- **THEN** `api` stays on the card and nothing waits on a request from it
