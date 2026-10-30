## RENAMED Requirements
- FROM: `### Requirement: Silence is stamped by the watchdog`
- TO: `### Requirement: Silence is read by the watchdog and recorded by the reducer`

## MODIFIED Requirements

### Requirement: Silence is read by the watchdog and recorded by the reducer
Each tick `WatchdogService` SHALL emit one `SessionSign.Read` per open task; a window it cannot list emits nothing.
Only the reducer writes what it finds into `TaskState.session`: token limit, crash, a launch exit, an API error ending
a turn, or a turn end with nothing newer behind it past the threshold: the same flip, a NEEDS YOU line, overruled at `NEW`.

#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted; logged and notified once per silence, a restart included, a new one alerting again
- **WHEN** a stopped session's worktree calls MCP
- **THEN** the card reads working and the banner is not repeated; the next read decides again

#### Scenario: A permission prompt
- **WHEN** a session sits at one and its report is plain
- **THEN** within seconds NEEDS INPUT, answer the session, no stopped banner; a plain turn end is silent until nothing moved for a while
- **THEN** the human gets one desktop notification naming its cause, not one per tick
- **WHEN** work starts again
- **THEN** a permission the prompt raised clears; one the session said stays, also after it reported

#### Scenario: A turn ends unreported, or a question answered in the terminal
- **WHEN** Claude ends its turn without reporting, or the turn that began with the human's terminal answer to its question does
- **THEN** it is sent back once to report first, so NEEDS INPUT does not outlive its answer; still unreported, it is silent

#### Scenario: The agent CLI never came up
- **WHEN** it exits at `NEW` before reporting (`Error: Invalid MCP configuration`), or its window shows its runtime's `trust-dialog`
- **THEN** within seconds NEEDS YOU: the session ended, its last lines (unread, the card points at the launch); the dialog: NEEDS INPUT, no key sent

#### Scenario: A turn ends on an API error
- **WHEN** the CLI ends a turn on an API error and names `overloaded`
- **THEN** NEEDS YOU at once with no threshold, saying its turn failed: overloaded, the CLI's own error word

#### Scenario: A session is cleared or resumed
- **WHEN** a session ends with a reason declared as `continuing-end`
- **THEN** it is recorded as `IDLE`, not as gone; no stop is recorded and no banner is sent

#### Scenario: A status jagt holds
- **WHEN** a task is in a status whose `Move.ownerOf` is AGENT
- **THEN** the watchdog alerts for it, but not `VERIFYING`, which jagt holds; every open task is still read (`WatchdogServiceTest`)

### Requirement: A hook reports, the watchdog decides
Blocking wording MUST be declared, never matched: `blocking-notification` in the runtime's properties →
`AgentRuntime.blockingNotification`, unrecognised wording staying the quieter. The stop SHALL be the reducer's
alone, on the transition only, cleared by any report (`withStatus`), a later alive sign winning by arrival.

#### Scenario: A stamped task reports
- **WHEN** any report arrives
- **THEN** the stop clears
