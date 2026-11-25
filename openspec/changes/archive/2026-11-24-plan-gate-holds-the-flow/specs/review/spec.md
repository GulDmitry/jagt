## ADDED Requirements

### Requirement: A round answered by a plan is relayed again
A round equal to the last one relayed SHALL be relayed again where the task reported `PLAN_PENDING` after that relay.

#### Scenario: A round left unfinished
- **WHEN** a round was relayed, the session reported `PLAN_PENDING`, and the next sweep reads the same round
- **THEN** it is relayed again, not called unchanged
