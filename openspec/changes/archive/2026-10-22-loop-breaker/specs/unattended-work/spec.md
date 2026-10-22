## ADDED Requirements

### Requirement: jagt stops a task it suspects it is looping
`LoopGuard` SHALL stop a task when `loopBreaker.stalledRounds` (default 3) relayed rounds running each left as many
threads as the one before with the same job failing, or when the Master would ship past `loopBreaker.ships`
(default 4) within `autoReview.windowHours`, counted since the last `lift`. Its status SHALL stay.

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with 10 threads open and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: Ships with progress
- **WHEN** ABC-42 shipped four times today while its threads fell from 10 to 5, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships in 24h: 4`

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`LoopStopGuard`). The board
SHALL ring it `jagt stopped`, its hover the evidence. The human's `lift` SHALL clear it and count anew.

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`

#### Scenario: The Master lifts
- **WHEN** the Master presses `lift`
- **THEN** it is refused: `lift` is a human's
