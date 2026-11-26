## ADDED Requirements

### Requirement: A job failing with one cause run after run is reported again
A job failing with one cause run after run SHALL log ERROR once, DEBUG after, and ERROR with `repeats` at most hourly.
#### Scenario: A job fails every minute
- **WHEN** a job fails with the same cause on every tick for over an hour
- **THEN** the log holds its first ERROR and a repeat ERROR carrying the runs since
