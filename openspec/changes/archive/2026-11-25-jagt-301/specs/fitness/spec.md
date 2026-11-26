## ADDED Requirements

### Requirement: A loosened threshold names the decision that allows it
`fitnessRaises` SHALL compare every tracked `fitness.yml` with its merge base against `main` and fail, naming the row,
the old and the new number, for each row whose `max` rose or `min` fell unless its `decision:` changed to a record under
`docs/decisions/`. A new row, a falling `max` and a rising `min` pass. The test gate runs it.
#### Scenario: A raise keeps its decision
- **WHEN** a branch raises a row's `max` from 58 to 59 and keeps `decision: "0176"`
- **THEN** `fitnessRaises` fails naming the row, 58 and 59
#### Scenario: A raise names a new record
- **WHEN** the raise changes `decision:` to a number whose record exists
- **THEN** `fitnessRaises` passes
#### Scenario: A floor falls
- **WHEN** a row's `min` falls and its decision stays
- **THEN** `fitnessRaises` fails
