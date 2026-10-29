## ADDED Requirements

### Requirement: A task has a ceiling of unattended moves an hour
jagt SHALL count, per task, the moves it made unasked in the last hour since the task's last `lift`: a history step whose origin is the Master
(`MasterPress`, `ConflictFinish`), auto-review or the tracker. A move by the board or by a session's MCP report SHALL NOT count. At
`loopBreaker.perHour` (default 20) the next Master press, conflict deploy, auto-review poll or round SHALL NOT be made and the task SHALL
stop, its status kept, as `PACE`; a human press SHALL never be refused. Tracker intake opens tasks and SHALL count nothing.

#### Scenario: Too many moves in an hour
- **WHEN** ABC-42 made 20 unattended moves in the last hour
- **THEN** the next is not made and the card asks `lift the stop`

#### Scenario: Moves older than the hour
- **WHEN** some of ABC-42's 20 unattended moves are over an hour old, or came before its last lift
- **THEN** the next is made

## MODIFIED Requirements

### Requirement: jagt stops a task it suspects it is looping
A not-ready plan or round, a failed verification or probe, or a review round SHALL count toward `loopBreaker.rounds` only while it answers the
session report the round before it answered.

#### Scenario: Reworked plans are work
- **WHEN** ABC-42's plan is returned a fourth time and each of the three before answered a different plan
- **THEN** it is relayed and nothing stops

## REMOVED Requirements

### Requirement: Unattended starts have a daily ceiling
### Requirement: Unattended starts are counted per day
