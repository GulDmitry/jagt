## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out

#### Scenario: A task past its review
- **WHEN** ABC-42 is `DEPLOYED`, `DEPLOY_CONFLICT` or `REVERTED` and its open request's pipeline dies on infrastructure
- **THEN** no retry is armed or sent and no `INFRA` stop is set
