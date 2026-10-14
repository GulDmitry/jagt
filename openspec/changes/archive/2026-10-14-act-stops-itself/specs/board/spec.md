## MODIFIED Requirements

### Requirement: Only news is on screen
The line under a card SHALL carry news only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU. The next poll or job run sits
in a tooltip; a stopped poll, failed job or stopped `act` gets its own mark. A status says itself in words; the
highlighted button says what to do. A badge is the human's move; a quiet "you can …" can wait.

#### Scenario: Jobs that run fine
- **WHEN** no job's last run failed
- **THEN** no jobs chip shows
