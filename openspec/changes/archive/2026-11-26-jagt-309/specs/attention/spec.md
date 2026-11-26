## MODIFIED Requirements

### Requirement: Attention tiers
`flow/view/Attention` SHALL name the tier, pinned per status in `MoveTest`. `REQUIRED` (a stopped or asking session, a round back, a red
run, a conflict,
a round nothing reads again): header count, own-move filter, alarm edge. `OPTIONAL` (an approval landed, their own revert or pause):
a card badge only. `NONE`
(owner not YOU): nothing.
#### Scenario: Owner not YOU
- **WHEN** `NONE`
- **THEN** nothing shows

### Requirement: Silence is read by the watchdog and recorded by the reducer
Each tick `WatchdogService` SHALL emit one `SessionSign.Read` per open task: its window's process (running, a dead shell, missing), print, log (named
by its session, on the record) and the threshold; a window it cannot list emits nothing.
#### Scenario: The window shows the process
- **WHEN** nothing moved for the threshold and the window runs its agent
- **THEN** quiet, "no sign of life, process running": still yours to look at
- **WHEN** the window holds no agent process, at any open status, `NEW` included (`Error: Invalid MCP configuration`)
- **THEN** within seconds the session is gone, its card line `NEEDS YOU: session silent: <cause>`, its last lines the cause (`WatchdogServiceTest`)
- **WHEN** a ship, the session-alive stop or the restart ask cannot list the windows
- **THEN** ship refuses naming the read, the stop stands, nothing is asked
