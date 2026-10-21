## ADDED Requirements

### Requirement: Only blocking, wrong and unproven stop a round

#### Scenario: A bug test red for another reason
- **WHEN** a bug fix's test fails before the fix, but not with the signal the report names
- **THEN** it is wrong, and the round is not ready
