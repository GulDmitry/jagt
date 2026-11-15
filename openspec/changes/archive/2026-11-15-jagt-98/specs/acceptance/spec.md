## MODIFIED Requirements

### Requirement: What a check found goes where it is owed
A row not met SHALL go to the session as an `acceptance-round` (`Step.ACCEPTANCE_ROUND`, origin `acceptance` named by the checker's role id),
the task `IN_PROGRESS`, counted as an unattended move toward `loopBreaker`. A row unprovable, or a verdict with no row, is a question
to you. A failed run stops the task for you (`ACCEPTANCE_FAILED`) and its lift runs the check once more.
A verdict on a sha that is no longer the deploy's tip is dropped.

#### Scenario: A row not met
- **WHEN** acceptance finds a row not met **THEN** the session receives the rows as an `acceptance-round`

#### Scenario: A second checker's row not met
- **WHEN** audit, a second checker, finds a row not met **THEN** the round is `by: audit`; a case `by: acceptance` does not send it

#### Scenario: A verdict on a sha off the deploy branch
- **WHEN** the checked sha is no longer the tip when the verdict arrives **THEN** it is dropped and changes nothing on the card
