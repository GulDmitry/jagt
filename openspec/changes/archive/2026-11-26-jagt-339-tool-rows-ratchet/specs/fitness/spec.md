## MODIFIED Requirements

### Requirement: A fitness function reports its number against its threshold
Every fitness function SHALL yield a measured number against a threshold with a direction, `max` or `min`; a zero fitness function is `max 0`.
The report SHALL name the id, the value, the threshold with its direction and every item. A counted kind below its stored number fails
asking to lower it ([0176](../../../docs/decisions/0176-every-kind-has-a-ceiling.md)), whoever measures it, unless the row holds `ratchet: false`.
#### Scenario: A fitness function fails
- **WHEN** a `max 0` fitness function measures two items
- **THEN** the report names its id, value 2, `max 0` and both items
#### Scenario: A counted kind falls below its number
- **WHEN** a counted kind measures fewer items than its stored number
- **THEN** it fails asking to lower the number to the measured one
#### Scenario: A tool-measured counted row falls below its number
- **WHEN** a counted row measured by a build tool measures fewer than its stored number
- **THEN** it fails naming the row and asking to lower the number to the measured one
#### Scenario: A row marked not to ratchet
- **WHEN** a counted row holds `ratchet: false` and measures fewer than its number
- **THEN** it passes
