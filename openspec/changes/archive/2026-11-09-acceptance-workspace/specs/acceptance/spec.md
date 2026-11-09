## MODIFIED Requirements

### Requirement: A checker is configured under its role
`jagt.yml` SHALL say only whether a checker is on: `orchestrator.roles.<id>.enabled`, off unless true.
Its `model`, `environments`, `flows` and `workspace` SHALL be read from `roles/<id>/role.yml` over its `.dist`, again on every check.
jagt SHALL refuse to start while `jagt.yml` writes one of them, or `qa` or `roles.qa`, naming where it now lives.

#### Scenario: No key names acceptance
- **WHEN** `jagt.yml` does not write `roles.acceptance`
- **THEN** acceptance is off

#### Scenario: The old keys
- **WHEN** `jagt.yml` writes `roles: {acceptance: {environments: [...]}}`, `qa: {enabled: true}` or `roles: {qa: {enabled: true}}`
- **THEN** the start is refused, naming `roles/acceptance/role.yml` or `roles.acceptance` respectively

## REMOVED Requirements

### Requirement: Acceptance writes nothing but its report
**Reason**: Acceptance may write its workspace, so the requirement is rewritten under its own capability.
**Migration**: `acceptance-workspace`, "Acceptance reads with a narrow set of tools" and "Acceptance writes nothing outside its workspace".
