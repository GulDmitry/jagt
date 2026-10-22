## MODIFIED Requirements

### Requirement: A log keeps one archive and a headless read none
`jagt-backend.log` and `jagt-master.log` SHALL roll at 10 MB and at each start into one `.1`, replacing the last. A headless read SHALL
keep no transcript (`--no-session-persistence`); a session's own transcripts are bounded by its agent CLI.

#### Scenario: A long run
- **WHEN** the Master's log passes 10 MB
- **THEN** it becomes `jagt-master.log.1` and the master window keeps following the new file

#### Scenario: A headless read ends
- **WHEN** a ticket read returns
- **THEN** nothing it said is left under the agent CLI's transcripts
