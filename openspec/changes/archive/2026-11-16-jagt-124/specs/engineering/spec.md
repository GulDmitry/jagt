## MODIFIED Requirements

### Requirement: Every gate stays wired
JaCoCo, PIT, Spotless, Checkstyle, Error Prone, the ArchUnit rule tests, the vulnerability scan and the pins SHALL stay wired into the build or CI.
#### Scenario: A gate is unwired
- **WHEN** a build file or the workflow stops wiring one of them **THEN** `QualityGatesTest` fails naming it; the board's gates: JAGT-96 checks them
#### Scenario: A pin is dropped
- **WHEN** a lockfile, the wrapper checksum, an action sha, an image digest or the update bot goes **THEN** `PinningTest` fails naming it
