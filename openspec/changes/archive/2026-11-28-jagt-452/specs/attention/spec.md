## MODIFIED Requirements

### Requirement: The reducer records what the watchdog read
Only the reducer SHALL write what it finds into `TaskState.session`: a dead process at any status, an API error ending a turn, or a turn end with
nothing newer behind it past the threshold: the same flip, a NEEDS YOU line, overruled at `NEW`. It alerts only where `Move.ownerOf` is AGENT, not
`VERIFYING`.
#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted; logged and notified once per silence, a restart included, a new one alerting again
- **WHEN** a stopped session's worktree calls MCP or runs a tool (throttled, no report), or a read finds a gone window running its agent
- **THEN** the card reads working and the banner is not repeated; the next read decides again
- **WHEN** after a turn end the window only prints (a repaint), with no call, tool use or log line
- **THEN** the turn end stands: the card never reads working, its quiet counts from the turn end
- **WHEN** a session ends with a reason declared as `continuing-end` (cleared, resumed)
- **THEN** it is recorded as `IDLE`, not as gone; no banner is sent
- **WHEN** Claude ends its turn without reporting, or the turn that began with the human's terminal answer to its question does
- **THEN** it is sent back once to report first, so NEEDS INPUT outlives no answer; still unreported, it is woken first (`cold-prompts`)
- **WHEN** a turn at a status the agent works in ends with an `OPEN QUESTIONS` line other than `none` and the report asks nothing
- **THEN** it is sent back once to report it as `outcome=question`
- **WHEN** the CLI ends a turn on an API error and names `overloaded`
- **THEN** NEEDS YOU at once with no threshold, saying its turn failed: overloaded, the CLI's own error word
- **WHEN** any report arrives
- **THEN** the stop clears
