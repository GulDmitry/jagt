## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `roles.master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and
answer a question. It SHALL press `add-repo` for a repository a session asked for (`MasterRepoJob`) and deploy a
`REVIEWED` or `APPROVED` task once (`MasterDeployJob`). A permission, and a plan asked for in `plan` mode, stay yours.

#### Scenario: A session stops to ask
- **WHEN** a session asks a question
- **THEN** the Master answers in its window, the task resumes; `mine: [answer]` keeps it yours; the same words asked again are a new question
