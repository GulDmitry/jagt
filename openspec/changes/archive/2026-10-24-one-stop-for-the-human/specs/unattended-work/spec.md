## MODIFIED Requirements

### Requirement: jagt stops a task it suspects it is looping
`TaskStops` SHALL stop a task, its status kept, on any trigger since the last `lift`:
`loopBreaker.stalledRounds` (3) relayed rounds running that each left as many threads open and the same job failing;
a Master ship past `loopBreaker.ships` (4) within `autoReview.windowHours`;
a failed verification or probe, a not-ready plan or round, repeats too, or a review round, sent back past `loopBreaker.rounds` (3) running in 24h.

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with 10 threads open and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: Ships with progress
- **WHEN** ABC-42 shipped five times today while its threads fell from 10 to 5, and the Master would ship again
- **THEN** it ships, and nothing stops it

#### Scenario: Ships going round
- **WHEN** ABC-42 shipped four times today while its threads went 5, 6, 5, 6, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships without a new low in 24h: 4`
- **AND** a ship counts only if its round left no fewer threads open than the lowest before it; the first round sets that low

#### Scenario: Red rounds running
- **WHEN** ABC-42's verification fails a fourth time with no other step sent between, or the Master returns it a fourth
- **THEN** nothing is sent, nobody is asked, it stops where it is, and the hover says `verify sent 3 rounds running in 24h`

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`StopGuard`). The board
SHALL ring it `jagt stopped`, its hover the evidence. Only a human's `lift` SHALL clear it and count anew.

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`
