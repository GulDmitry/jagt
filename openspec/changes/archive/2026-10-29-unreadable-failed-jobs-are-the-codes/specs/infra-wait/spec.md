## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out

#### Scenario: The failed jobs cannot be read
- **WHEN** the CLI is missing, not logged in, or the host refuses to list ABC-42's failed jobs
- **THEN** the red is the code's and the round goes to the session as before, one warning naming the cause

#### Scenario: The re-run is refused
- **WHEN** the host or the CLI refuses to re-run jobs already read as infrastructure
- **THEN** the task stops as `INFRA` at once, the evidence being the CLI's or the host's own words
