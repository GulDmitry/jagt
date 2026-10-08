## MODIFIED Requirements

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status a session reports over MCP, refused unless `FlowRules.refusedReport` allows
it. `REVIEWED` and `APPROVED` MUST land only through `FlowReports.read`, jagt's round read on the host. The
refusal MUST own the reason the agent acts on: no task talks itself onto a shared branch, out of one, closed, or
past its review.

#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason

#### Scenario: A session approves itself
- **WHEN** a session reports `APPROVED`
- **THEN** it is refused; only the round read lands it

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps `REVERTED`, and `DEPLOY_CONFLICT`
until deployed, recording the line: refusing errors every call of that session. A round read MUST leave a `DEPLOYED`,
`DEPLOY_CONFLICT` or `DONE` task in place: shipped work never goes back to approval.

#### Scenario: A report at REVERTED
- **WHEN** a `REVERTED` task's agent reports
- **THEN** the task stays, the line recorded

#### Scenario: An approval after deploy
- **WHEN** a poll reads `APPROVED` past `DEPLOYED`
- **THEN** the task stays
