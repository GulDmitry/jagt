## MODIFIED Requirements

### Requirement: What a check found goes where it is owed
A row not met SHALL go to the session as an `acceptance-round` (`Step.ACCEPTANCE_ROUND`), the task `IN_PROGRESS`.
Its origin is `acceptance`, an unattended move toward `loopBreaker`, with the checker's role id as `role`; none reads `acceptance`.
A row unprovable, or a verdict with no row, is a question to you. A failed run stops the task for you (`ACCEPTANCE_FAILED`)
and its lift runs the check once more. A verdict on a sha that is no longer the deploy's tip is dropped.

#### Scenario: A second checker's row not met
- **WHEN** audit, a second checker, finds a row not met **THEN** the round is `by: audit`, labelled `audit`; a case `by: acceptance` does not send it
