## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out

#### Scenario: The probe records what it read
- **WHEN** a task stopped as `INFRA` is read green after the outage
- **THEN** the task's checks and approval read that green, not the pre-outage word
- **AND** the read writes nothing else: no relay, no status move, no message to the session
