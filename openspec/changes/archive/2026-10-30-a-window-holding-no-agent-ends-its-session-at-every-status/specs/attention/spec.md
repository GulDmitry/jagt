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
- **WHEN** a stopped session's worktree calls MCP
- **THEN** the card reads working and the banner is not repeated; the next read decides again
- **WHEN** a session ends with a reason declared as `continuing-end` (cleared, resumed)
- **THEN** it is recorded as `IDLE`, not as gone; no banner is sent

#### Scenario: The window shows the process
- **WHEN** nothing moved for the threshold and the window runs its agent
- **THEN** quiet, "no sign of life, process running": still yours to look at
- **WHEN** the window holds no agent process, at any open status, `NEW` included (`Error: Invalid MCP configuration`)
- **THEN** within seconds the session is gone, NEEDS YOU, its last lines the cause (`WatchdogServiceTest`)
- **WHEN** a ship, the session-alive stop or the restart ask cannot list the windows
- **THEN** ship refuses naming the read, the stop stands, nothing is asked

#### Scenario: A permission prompt
- **WHEN** a session sits at one and its report is plain
- **THEN** within seconds NEEDS INPUT, answer the session, no stopped banner, no key sent; a plain turn end is silent until nothing moved for a while
- **WHEN** it is the runtime's `trust-dialog` at `NEW`, jagt reads the window alone
- **THEN** the human gets one desktop notification naming its cause, not one per tick
- **WHEN** work starts again
- **THEN** a permission the prompt raised clears; one the session said stays, also after it reported

#### Scenario: A turn ends unreported, or a question answered in the terminal
- **WHEN** Claude ends its turn without reporting, or the turn that began with the human's terminal answer to its question does
- **THEN** it is sent back once to report first, so NEEDS INPUT does not outlive its answer; still unreported, it is silent

#### Scenario: A turn ends on an API error
- **WHEN** the CLI ends a turn on an API error and names `overloaded`
- **THEN** NEEDS YOU at once with no threshold, saying its turn failed: overloaded, the CLI's own error word
