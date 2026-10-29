## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out
A stop this sets (`INFRA`, `CHECKS`) SHALL end on the first read listing the task's checks not red, or on a read showing a request it was counted on gone or closed unmerged, or no request linked.

#### Scenario: The request is gone
- **WHEN** a task stopped as `INFRA` or `CHECKS` and the host says a request it was counted on no longer exists or was closed
- **THEN** the stop is voided and the log says `stop voided by read` once, with `request=gone`
- **AND** a request the stop was not counted on that is gone leaves the stop standing
- **AND** a read refused because the host or CLI cannot answer leaves the stop standing

#### Scenario: The request is unlinked
- **WHEN** a task stopped as `INFRA` or `CHECKS` has no request linked any more
- **THEN** the stop is voided as above
