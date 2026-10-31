## ADDED Requirements

### Requirement: A declined round holds DEPLOYED
A session answering a `qa-round` with `no_changes` SHALL leave the task DEPLOYED, since nothing on the deploy changed.
Its reply is the only door that lands the task there from IN_PROGRESS.

#### Scenario: The session disputes a row
- **WHEN** ABC-42 is IN_PROGRESS on a `qa-round` and its session reports `no_changes` with a reason
- **THEN** ABC-42 is DEPLOYED again and the reply stands as its report
