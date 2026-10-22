## MODIFIED Requirements

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps `REVERTED`, and `DEPLOY_CONFLICT`
until deployed: refusing errors every call of that session. Its card SHALL keep the status's line
(`FlowRules.holdsItsLine`), the report's logged; a question alone reaches it, until the next report. A round read
MUST leave a `DEPLOYED`, `DEPLOY_CONFLICT` or `DONE` task in place: shipped work never goes back to approval.

#### Scenario: A report at a held status
- **WHEN** a `DEPLOY_CONFLICT` task's agent reports `no changes`
- **THEN** the task stays and its card keeps the conflict, the report's line logged

#### Scenario: An approval after deploy
- **WHEN** a poll reads `APPROVED` past `DEPLOYED`
- **THEN** the task stays
