## ADDED Requirements

### Requirement: Metrics and build info answer the owner
`/actuator/metrics` and `/actuator/info` SHALL answer a logged-in owner and refuse every other caller; neither is a public path.
`metrics` SHALL carry the JVM's and `http.server.requests`; `info` SHALL carry the build version.
The board's title SHALL name that version in its hover.

#### Scenario: The owner reads the numbers
- **WHEN** the owner is logged in and asks `/actuator/metrics` and `/actuator/info`
- **THEN** metrics list `jvm.memory.used` and `http.server.requests`, and info holds `build.version`

#### Scenario: Nobody is logged in
- **WHEN** a caller with no login asks `/actuator/metrics` or `/actuator/info`
- **THEN** it is refused

#### Scenario: The board's title
- **WHEN** the owner hovers the board's title
- **THEN** the hover reads `jagt` and the running version
