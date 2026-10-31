## ADDED Requirements

### Requirement: The Master is configured under its role
The Master SHALL be configured under `orchestrator.roles.master`, with `mode`, `model`, `brief` and `mine`. The old
`orchestrator.master` key SHALL still be read; where `roles.master` is written, it wins. Reading the old key logs one
deprecation line and is never a startup problem.

#### Scenario: Only the old key
- **WHEN** `jagt.yml` writes `master: {mode: act}` and no `roles.master`
- **THEN** the Master acts, and one deprecation line is logged

#### Scenario: Both keys
- **WHEN** `jagt.yml` writes `master: {mode: act}` and `roles: {master: {mode: judge}}`
- **THEN** the Master judges
