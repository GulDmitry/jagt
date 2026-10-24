## MODIFIED Requirements

### Requirement: Every deploy is checked where it landed
`DeployCheckJob` SHALL ask the session once per deploy commit, keyed on the task's turn, so neither another relay nor
a restart asks again; what it asks is the brief's `deploy` step. Green MUST report nothing, the task staying
`DEPLOYED`; red is the session's `IN_PROGRESS`, the round running again: reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`
