## MODIFIED Requirements

### Requirement: jagt stops a task it suspects it is looping
`LoopGuard` SHALL stop a task after `loopBreaker.stalledRounds` (3) relayed rounds running that each left as many
threads open and the same job failing. It SHALL stop one before the Master ships past `loopBreaker.ships` (4) within
`autoReview.windowHours` since the last `lift`. A ship counts only if the round it answers did not bring the open
threads below the lowest of the rounds before it; the first round sets that low. Its status SHALL stay.

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with 10 threads open and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: Ships with progress
- **WHEN** ABC-42 shipped five times today while its threads fell from 10 to 5, and the Master would ship again
- **THEN** it ships, and nothing stops it

#### Scenario: Ships going round
- **WHEN** ABC-42 shipped four times today while its threads went 5, 6, 5, 6, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships without a new low in 24h: 4`
