## ADDED Requirements

### Requirement: A verification command reruns clean
The session's standing rules SHALL require a verification command to set up and reset its own state, and to pass twice in a
row before the session reports `REVIEW_PENDING`; a manual step goes into the command.

#### Scenario: A check passing only after a manual reset
- **WHEN** a session's check passed only after it reset a database by hand
- **THEN** the session puts the reset into the command and runs it twice before reporting `REVIEW_PENDING`
