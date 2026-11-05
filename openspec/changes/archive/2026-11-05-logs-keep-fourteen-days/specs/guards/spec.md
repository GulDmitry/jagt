## MODIFIED Requirements

### Requirement: A log keeps one archive and a headless read none
`jagt-backend.log` and `jagt-master.log` SHALL roll at 10 MB into one `.1`, replacing the last. At each start the previous `.1`
SHALL become `<log>.<UTC yyyy-MM-dd-HHmmss>` and the log `.1`; dated archives SHALL be deleted past 14 days, then oldest first
past 200 MB in total. A headless read SHALL keep no transcript (`--no-session-persistence`); a session's own transcripts are
bounded by its agent CLI.

#### Scenario: A long run
- **WHEN** the Master's log passes 10 MB
- **THEN** it becomes `jagt-master.log.1` and the master window keeps following the new file

#### Scenario: A headless read ends
- **WHEN** a ticket read returns
- **THEN** nothing it said is left under the agent CLI's transcripts
