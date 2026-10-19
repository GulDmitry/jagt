## MODIFIED Requirements

### Requirement: Silence is stamped by the watchdog
`WatchdogService` SHALL stamp silence from `service/SessionProbe` (`TaskState.silentSince`, `TaskState.silentBecause`):
token limit, crash, unanswered prompt, a turn the CLI ended on an API error, or `SessionProbe.State.IDLE` at the shared
threshold with nothing newer behind it: the same flip, a NEEDS YOU line, overruled at `NEW`. A failed turn needs no
threshold and names the CLI's own error word. A session ended for a reason declared as `continuing-end` is recorded as
`IDLE`, not as gone. It MUST watch every status whose `Move.ownerOf` is AGENT but `VERIFYING`, which jagt holds
(`WatchdogServiceTest`).

#### Scenario: Silent stop
- **WHEN** a session stops unannounced
- **THEN** NEEDS YOU, Focus highlighted

#### Scenario: A permission prompt
- **WHEN** a session sits at one
- **THEN** it is reported within seconds; a plain turn end is silent until nothing moved for a while

#### Scenario: A turn ends unreported
- **WHEN** Claude ends its turn without reporting
- **THEN** it is sent back once to report first, then stamped silent

#### Scenario: The agent CLI never came up
- **WHEN** you `focus <task>` at `NEW`
- **THEN** the card points at the launch

#### Scenario: A turn ends on an API error
- **WHEN** the CLI ends a turn on an API error and names `overloaded`
- **THEN** NEEDS YOU at once, saying its turn failed: overloaded

#### Scenario: A session is cleared or resumed
- **WHEN** a session ends with a reason declared as `continuing-end`
- **THEN** no stop is stamped and no banner is sent
