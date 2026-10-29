## MODIFIED Requirements

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps `REVERTED`, and `DEPLOY_CONFLICT` until deployed: refusing errors every
call of that session. Its card SHALL keep the status's line (`FlowRules.holdsItsLine`), the report's logged; a question alone reaches it, until the
next report. A round read MUST move only a task waiting on the host (`FlowRules.waitsOnTheHost`) and leave every other status in place, its checks
alone updated: shipped work never goes back to approval, a session's work is never overwritten. It MUST leave a `QUESTION` or `PERMISSION` standing
(`FlowRules.standsThroughARead`): only the session's next report or a human answers it.

#### Scenario: A report at a held status
- **WHEN** a `DEPLOY_CONFLICT` task's agent reports `no changes`
- **THEN** the task stays and its card keeps the conflict, the report's logged
- **AND** a poll reading `APPROVED` past `DEPLOYED` leaves the task there
- **AND** a poll reading ABC-42 green leaves its `QUESTION` asking, the question on the card
- **AND** a poll reading ABC-42 approved at `IN_PROGRESS` leaves it there, its checks and approval recorded
