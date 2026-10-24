## MODIFIED Requirements

### Requirement: jagt stops a task it suspects it is looping
`LoopGuard` SHALL stop a task, its status kept, on any trigger since the last `lift`:
`loopBreaker.stalledRounds` (3) relayed rounds running that each left as many threads open and the same job failing;
a Master ship past `loopBreaker.ships` (4) within `autoReview.windowHours`;
a failed verification or probe, a not-ready plan or round, or a review round, sent back a fourth time within 24h.

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
- **THEN** nothing is sent, it stops where it is, and the hover says `verify sent 3 rounds running in 24h`
