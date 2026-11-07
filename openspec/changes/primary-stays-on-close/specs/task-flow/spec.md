## ADDED Requirements

### Requirement: A closed request never takes the primary repository out of the task
The host saying the request of a task's primary repository was closed unmerged SHALL NOT remove it, whatever else the task holds:
the sweep calls it gone as for a task's only repository.

#### Scenario: The primary request closed
- **WHEN** ABC-42 spans `alpha` and `beta`, `alpha` primary, and the host says `alpha`'s request was closed unmerged
- **THEN** `alpha` stays in the task with its checkout, and the sweep calls it gone, naming the request
