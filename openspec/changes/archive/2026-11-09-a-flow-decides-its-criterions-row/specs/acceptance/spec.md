## ADDED Requirements

### Requirement: A flow decides the row of the criterion it names
The read's `flows` answer SHALL name per flow its `id` and the `criterion` it checks, as written in a row's `criterion`.
A flow naming a criterion SHALL decide that criterion's row: `passed` makes it met, `failed` not met, `unprovable` leaves
the read's row standing with the flow's reason added. A failed flow outranks a passed one. Deciding the last unprovable row drops the `question`.
A flow naming no criterion, or one no row has, is a row of its own.

#### Scenario: A passed flow proves a row the read could not
- **WHEN** the read calls C1 unprovable behind a login, asks how to sign in, and its flow `open-page` for C1 passes
- **THEN** C1 is met, the question is dropped and the check ends `accepted`

#### Scenario: A failed flow
- **WHEN** the read calls C1 met and its flow `open-page` for C1 fails with "no button"
- **THEN** C1 is not met, citing the flow and "no button"

#### Scenario: A flow that could not be run
- **WHEN** the read calls C1 unprovable and its flow for C1 is unprovable with "no JSON report"
- **THEN** C1 stays unprovable, its evidence adds "no JSON report", and the question stands
