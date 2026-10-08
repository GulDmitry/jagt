## MODIFIED Requirements

### Requirement: Silence is stamped by the watchdog
`WatchdogService` SHALL stamp silence from `service/SessionProbe` (`TaskState.silentSince`, `TaskState.silentBecause`):
token limit, crash, unanswered prompt, or `SessionProbe.State.IDLE` at the shared threshold with nothing newer
behind it: the same flip, a NEEDS YOU line, overruled at `NEW`. It MUST watch every status whose `Move.ownerOf`
is AGENT but `VERIFYING`, which jagt holds (`WatchdogServiceTest`).

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
