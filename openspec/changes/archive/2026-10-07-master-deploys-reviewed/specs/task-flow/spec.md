## MODIFIED Requirements

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps `REVERTED`, and `DEPLOY_CONFLICT`
until deployed, recording the line. Refusing errors every call of that session; passing the next status
erases the revert and launders the `CI_POLLING` guard through `IN_PROGRESS`. `REVIEWED` or `APPROVED` read off the request MUST leave a `DEPLOYED`,
`DEPLOY_CONFLICT` or `DONE` task in place, or a poll drags shipped work back to approval.

#### Scenario: A report at REVERTED
- **WHEN** a `REVERTED` task's agent reports
- **THEN** the task stays, the line recorded

#### Scenario: An approval after deploy
- **WHEN** a poll reads `APPROVED` past `DEPLOYED`
- **THEN** the task stays
