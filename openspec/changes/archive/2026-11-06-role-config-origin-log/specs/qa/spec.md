## MODIFIED Requirements

### Requirement: A checker is configured under its role
A checker SHALL be configured under `orchestrator.roles.<id>`, with `enabled`, `model` and `environments`. It is off unless
`enabled` is true. The old `orchestrator.qa` key SHALL still be read for the role `qa`; where `roles.qa` is written, it wins.
Reading the old key logs one deprecation line and is never a startup problem.
At start jagt logs, per installed role, one `role config` line for each of `enabled`, `model` and `environments`
(its count), naming the `origin`: `jagt.yml`, `role.yml`, `role.yml.dist` or `default`.

#### Scenario: Neither key names QA
- **WHEN** `jagt.yml` writes neither `roles.qa` nor `qa`
- **THEN** QA is off

#### Scenario: Only the old key
- **WHEN** `jagt.yml` writes `qa: {enabled: true}` and no `roles.qa`
- **THEN** QA is on, and one deprecation line is logged

#### Scenario: Both keys
- **WHEN** `jagt.yml` writes `qa: {enabled: true}` and `roles: {qa: {enabled: false}}`
- **THEN** QA is off

#### Scenario: A model the human's copy names
- **WHEN** `jagt.yml` writes no `roles.qa.model` and `roles/qa/role.yml` names `model: haiku`
- **THEN** the start logs `role config` with `role=qa key=model value=haiku origin=role.yml`
