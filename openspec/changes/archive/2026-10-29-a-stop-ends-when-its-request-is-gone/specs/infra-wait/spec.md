## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out

#### Scenario: The request is gone
- **WHEN** a task stopped as `INFRA` or `CHECKS` and the host says the request the stop was counted on no longer exists
- **THEN** the stop is voided and the log says `stop voided by read` once, with `request=gone`
- **AND** a read refused because the host or CLI cannot answer leaves the stop standing
