# debug-reads Specification

## Purpose
Reading a running jagt's records and sessions from outside, over its own door, with no way to change it.

## Requirements

### Requirement: A running jagt is read, never changed, through its own door
`read_record` (`backend-log`, `master-log`, `stops`, `stops-reviewed`; the last `lines`, at most 1000) and `read_transcript`
(a task's conversation, newest `maxChars`) SHALL be `Audience.ROOT` and `Effect.REPORTS`; `GET /api/debug/records/{record}` and
`GET /api/debug/transcript` answer the same reads under `/api`. State and reports are read by `list_tasks` and `GET /state`.
A read SHALL change no state, file or session.

#### Scenario: A local CLI reads through a port-forward
- **WHEN** a CLI reaches `/mcp` through a port-forward with the Master token and calls `read_record` or `read_transcript`
- **THEN** it receives the tail of the record or the task's last words

#### Scenario: No login, no token
- **WHEN** a debug read comes with no login on `/api`, or with no token and no worktree on `/mcp`
- **THEN** it is refused 401

#### Scenario: A sub-agent reads
- **WHEN** a sub-agent calls `read_record` or `read_transcript`
- **THEN** it is refused as `permission`

#### Scenario: A read changes nothing
- **WHEN** a debug read runs
- **THEN** `state.json`, the records and every session are as they were, and a record not yet written is not created

#### Scenario: Console logging
- **WHEN** `orchestrator.logging` is `console` and `read_record` asks for `backend-log` or `master-log`
- **THEN** it returns the lines stdout carried

#### Scenario: A role names its own log
- **WHEN** an installed role contributes a log file and `read_record` asks for the record it names
- **THEN** it returns that file's tail, and `surface` lists no role's record itself

### Requirement: A debug read stays within its bounds
`read_record` SHALL read 1 to 1000 `lines` of a known record, 200 where `lines` is left out.
`read_transcript` SHALL keep 1 to 100000 `maxChars`, 16000 where left out.
A value outside, or an unknown record, SHALL be refused naming its field, and nothing is read.
#### Scenario: A read outside its bounds
- **WHEN** `read_record` asks for 0 or 1001 `lines`, or for a record that is none of the four, or `read_transcript` for 0 or 100001 `maxChars`
- **THEN** it is refused naming `lines`, `record` or `maxChars`
#### Scenario: A bound left out
- **WHEN** `read_record` leaves `lines` out, or `read_transcript` leaves `maxChars` out
- **THEN** it returns the last 200 lines, or the newest 16000 characters
