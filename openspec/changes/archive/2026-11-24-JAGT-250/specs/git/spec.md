## ADDED Requirements

### Requirement: A repository without a deployBranch is skipped
`deploy` SHALL land every repository of the task that names a `deployBranch`, and its outcome SHALL name each repository it skipped for naming none.
A skipped repository is never recorded as a deployment target. A task with no such repository, or one whose `deployBranch` names its `baseBranch`, MUST be refused.

#### Scenario: One repository has no deployBranch
- **WHEN** a task spans `api` and `web` and `web` has no `deployBranch`
- **THEN** `api` deploys and the outcome says `web` was skipped, no `deployBranch` configured
- **AND** the deployment record holds `api` alone

#### Scenario: No repository has a deployBranch
- **WHEN** no repository of the task has a `deployBranch`
- **THEN** the deploy is refused, nothing pushed
