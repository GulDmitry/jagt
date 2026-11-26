## MODIFIED Requirements

### Requirement: Only news is on screen
The line under a card SHALL carry news only: NEEDS INPUT, ANSWERED, PROBLEM, NEEDS YOU. The next poll or job run sits
in a tooltip; a stopped poll, failed job or stopped `act` gets its own mark. A status says itself in words; the
highlighted button says what to do. A badge is the human's move; a quiet "you can …" can wait.
#### Scenario: A deployed task
- **WHEN** a task is deployed; **THEN** it wears no badge: `done` is its only move
- **AND** while no job's last run failed, no jobs chip shows; once one threw, its hover names it and its cause until its next clean run
