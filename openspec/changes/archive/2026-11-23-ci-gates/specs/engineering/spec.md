## MODIFIED Requirements

### Requirement: Every run ends within a limit
A suite, a gate and a CI job SHALL end within a limit set from its measured time. A flaky test SHALL be registered or fixed from its cause.
#### Scenario: A suite or a gate overruns
- **WHEN** a run passes its limit **THEN** the build names the rule, run and limit: `suite-minutes`, `gate-minutes-heavy`, `gate-minutes-stand`
- **AND** with `-Ptimed=false`, as on CI, no run gets a limit, no time row runs and no suite time is recorded
#### Scenario: A run declares no limit
- **WHEN** a test task or a CI job declares none **THEN** `unlimited-runs` or `ci-jobs-without-timeout` fails

### Requirement: An open gap is listed by what is missing
A gate no stack meets yet SHALL be listed here by what is missing, never silent, each gap once. The work closing it SHALL write the gate above.
#### Scenario: The open gaps
- **WHEN** `pitestDiff` or the release gate runs **THEN** no rule asserts its limit: gap
- **AND** a changed board JS line gets Stryker on demand, never per diff: gap
