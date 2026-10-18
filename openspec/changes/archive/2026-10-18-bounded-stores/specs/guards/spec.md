## ADDED Requirements

### Requirement: A record store packs its older half
`finished.jsonl` past 10 MB and `artifacts/` past 1000 task directories SHALL pack their older half beside them,
`finished-<stamp>.jsonl.gz` and `artifacts/archive-<stamp>.zip`, keeping the newer half where every reader looks.
An artifact past 10 MB SHALL NOT be copied; `<name>.not-kept` names its size.

#### Scenario: The finished log passes its ceiling
- **WHEN** `done` appends a line past 10 MB
- **THEN** the older half is packed beside it and the `finished` report names the archive
- **AND** an intake still sees the newer half's ids

#### Scenario: The artifacts store is full
- **WHEN** `done` leaves the 1000th task directory
- **THEN** the older 500 are zipped into one archive and removed

#### Scenario: A runaway document
- **WHEN** a finished task's `plan.md` is 12 MB
- **THEN** its artifacts hold `plan.md.not-kept` instead

### Requirement: A log keeps one archive and a headless read none
`jagt-backend.log` and `jagt-master.log` SHALL roll at 10 MB into one `.1`, replacing the last. A headless read SHALL
keep no transcript (`--no-session-persistence`); a session's own transcripts are bounded by its agent CLI.

#### Scenario: A long run
- **WHEN** the Master's log passes 10 MB
- **THEN** it becomes `jagt-master.log.1` and the master window keeps following the new file

#### Scenario: A headless read ends
- **WHEN** a ticket read returns
- **THEN** nothing it said is left under the agent CLI's transcripts
