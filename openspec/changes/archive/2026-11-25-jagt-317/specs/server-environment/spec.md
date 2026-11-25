## ADDED Requirements

### Requirement: A start creates the temporary and cache folders on the volume
The entrypoint SHALL create `TMPDIR` and `XDG_CACHE_HOME` before jagt starts when either lies under `ORCHESTRATOR_DATA`.
A folder outside it SHALL be left alone.

#### Scenario: An empty volume
- **WHEN** the container starts with `TMPDIR=/data/tmp` and the volume is empty
- **THEN** the folder exists before the JVM starts
