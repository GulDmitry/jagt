## ADDED Requirements

### Requirement: An image scan fails on a published fix
The scan of a published base image SHALL fail only on a finding whose fixed version is published, and the image SHALL upgrade its packages when built.
#### Scenario: An OS package has no fix
- **WHEN** the scan lists a finding with no fixed version
- **THEN** the job passes

#### Scenario: An OS package has a published fix
- **WHEN** the scan lists a finding with a fixed version
- **THEN** the job fails naming it
