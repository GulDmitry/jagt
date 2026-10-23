## MODIFIED Requirements

### Requirement: Every deploy is checked where it landed
`DeployCheckJob` SHALL ask the session once per deploy commit and jagt run, keyed off what it asked, not the context
file other relays overwrite; what it asks is the brief's `deploy` step. Green MUST report nothing, the task staying
`DEPLOYED`; red is the session's `IN_PROGRESS`, the round running again: reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`
