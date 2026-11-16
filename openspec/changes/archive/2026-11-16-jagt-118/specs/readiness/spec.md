## ADDED Requirements

### Requirement: Pod liveness
`/actuator/health/liveness` SHALL answer 200 while the application's own state is correct and 503 once it is broken.
It SHALL be a public path of `contracts/openapi.yaml`, answered with no login and from a non-loopback address.
It SHALL NOT depend on a project clone, the start's checks or any system outside jagt.

#### Scenario: A clone is running
- **WHEN** a project's clone runs and nobody is logged in
- **THEN** the liveness path answers 200
