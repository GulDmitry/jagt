## MODIFIED Requirements

### Requirement: Field and consistency rules, one report
Field rules SHALL catch a value out of its enum, a missing required field, a link nobody can open, a question with no
message. Consistency rules catch two fields that cannot both hold: `reviewRequests` beside `reviewRequestUrl`,
CI_POLLING with no request, a request filed under a project the task does not have.

#### Scenario: CI_POLLING without a request, a question without a message
- **WHEN** a message reports CI_POLLING without one, or `outcome=question` with no message
- **THEN** it is refused, naming `reviewRequestUrl` or `message`, and nothing reaches the board or the Master
