## MODIFIED Requirements

### Requirement: The Master is configured under its role
The Master SHALL be configured under `orchestrator.roles.master`, with `mode`, `model`, `brief` and `mine`. The old
`orchestrator.master` key SHALL still be read; `roles.master` wins field by field and the old key fills each field
it leaves unwritten. Reading the old key logs one
deprecation line and is never a startup problem.

#### Scenario: Only the old key
- **WHEN** `jagt.yml` writes `master: {mode: act}` and no `roles.master`
- **THEN** the Master acts, and one deprecation line is logged

#### Scenario: Both keys
- **WHEN** `jagt.yml` writes `master: {mode: act}` and `roles: {master: {mode: judge}}`
- **THEN** the Master judges

#### Scenario: A partial role key
- **WHEN** `jagt.yml` writes `master: {mine: [plan, deploy, revert]}` and `roles: {master: {mode: act}}`
- **THEN** the Master acts and `mine` stays `[plan, deploy, revert]`
