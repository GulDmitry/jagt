## MODIFIED Requirements

### Requirement: Every run ends within a limit
A suite and a gate SHALL have a ceiling: an overrun warns, 3x stops it as hung, only fresh runs show a slowdown.
#### Scenario: A suite or a gate overruns
- **WHEN** a suite runs past its ceiling and passes **THEN** it is green and one WARN line names suite, minutes, load and power state
- **AND** a run at 3x its ceiling is stopped, named hung; with `-Ptimed=false`, as on CI, no run gets a limit or a time record
#### Scenario: A run declares no limit
- **WHEN** a test task or a CI job declares none **THEN** `unlimited-runs` or `ci-jobs-without-timeout` fails
