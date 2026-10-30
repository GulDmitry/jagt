## MODIFIED Requirements

### Requirement: Silence is read by the watchdog and recorded by the reducer
Each tick `WatchdogService` SHALL emit one `SessionSign.Read` per open task: its window's process (running, a dead
shell, missing), print, log (named by its session, on the record) and the threshold; a window it cannot list emits nothing.
Only the reducer writes what it finds into `TaskState.session`: a dead process at any status, an API error ending a turn, or a turn end with nothing
newer behind it past the threshold: the same flip, a NEEDS YOU line, overruled at `NEW`. It alerts only where
`Move.ownerOf` is AGENT, not `VERIFYING`.
