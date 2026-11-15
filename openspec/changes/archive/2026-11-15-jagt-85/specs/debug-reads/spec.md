## ADDED Requirements

### Requirement: A running jagt is read, never changed, through its own door
`read_record` (`backend-log`, `master-log`, `stops`, `stops-reviewed`; the last `lines`, at most 1000) and `read_transcript`
(a task's conversation, newest `maxChars`) SHALL be `Audience.MASTER` and `Effect.REPORTS`; `GET /api/debug/records/{record}` and
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
