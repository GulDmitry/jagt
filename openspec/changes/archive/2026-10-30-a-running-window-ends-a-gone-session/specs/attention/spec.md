## MODIFIED Requirements

### Requirement: Silence is read by the watchdog and recorded by the reducer
Each tick `WatchdogService` SHALL emit one `SessionSign.Read` per open task: its window's process (running, a dead
shell, missing), print, log and the threshold; a window it cannot list emits nothing. Only the reducer writes what it
finds into `TaskState.session`: a dead process at any status, an API error ending a turn, or a turn end with nothing
newer behind it past the threshold: the same flip, a NEEDS YOU line, overruled at `NEW`. It alerts only where
`Move.ownerOf` is AGENT, not `VERIFYING`.

#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted; logged and notified once per silence, a restart included, a new one alerting again
- **WHEN** a stopped session's worktree calls MCP, or a read finds a gone session's window running its agent
- **THEN** the card reads working and the banner is not repeated; the next read decides again
- **WHEN** a session ends with a reason declared as `continuing-end` (cleared, resumed)
- **THEN** it is recorded as `IDLE`, not as gone; no banner is sent
