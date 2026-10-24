## MODIFIED Requirements

### Requirement: Silence is stamped by the watchdog
`WatchdogService` SHALL stamp silence from `service/SessionProbe` (`TaskState.silentSince`, `TaskState.silentBecause`):
token limit, crash, a launch exit, unanswered prompt, an API error ending a turn, or `SessionProbe.State.IDLE` at the shared
threshold with nothing newer behind it: the same flip, a NEEDS YOU line, overruled at `NEW`.

#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted

#### Scenario: A permission prompt
- **WHEN** a session sits at one
- **THEN** it is reported within seconds; a plain turn end is silent until nothing moved for a while

#### Scenario: A turn ends unreported
- **WHEN** Claude ends its turn without reporting
- **THEN** it is sent back once to report first, then stamped silent

#### Scenario: A question answered in the terminal
- **WHEN** a session asked, the human answered in its terminal, and the turn that answer began ends without a report
- **THEN** it is sent back once to report first, so NEEDS INPUT does not outlive its answer

#### Scenario: The agent CLI never came up
- **WHEN** it exits at `NEW` before reporting, printing `Error: Invalid MCP configuration`
- **THEN** NEEDS YOU within seconds: the session ended, and its last lines; unread, the card points at the launch

#### Scenario: A turn ends on an API error
- **WHEN** the CLI ends a turn on an API error and names `overloaded`
- **THEN** NEEDS YOU at once with no threshold, saying its turn failed: overloaded, the CLI's own error word

#### Scenario: A session is cleared or resumed
- **WHEN** a session ends with a reason declared as `continuing-end`
- **THEN** it is recorded as `IDLE`, not as gone; no stop is stamped and no banner is sent

#### Scenario: A status jagt holds
- **WHEN** a task is in a status whose `Move.ownerOf` is AGENT
- **THEN** the watchdog watches it, but not `VERIFYING`, which jagt holds (`WatchdogServiceTest`)
