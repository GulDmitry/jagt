## MODIFIED Requirements

### Requirement: jagt stops a task it suspects it is looping
`TaskStops` SHALL stop a task, its status kept, on any trigger since the last `lift`:
`loopBreaker.stalledRounds` (3) relayed rounds running that each brought no new note and left the same job failing;
a Master ship past `loopBreaker.ships` (4) within `autoReview.windowHours` that answered a round bringing no new note;
a failed verification or probe, a not-ready plan or round, repeats too, or a review round, sent back past `loopBreaker.rounds` (3) running in 24h.
A new thread or a new note, by anyone, is progress: it resets the count.

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with the same newest notes and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: A new note resets the count
- **WHEN** two relayed rounds brought no new note, the third a new note on a thread, the fourth the same notes again
- **THEN** one round counts toward `stalledRounds`, and nothing stops the task

#### Scenario: Ships with progress
- **WHEN** ABC-42 shipped five times today and every round before a ship brought a new note, and the Master would ship again
- **THEN** it ships, and nothing stops it

#### Scenario: Ships going round
- **WHEN** ABC-42 shipped four times today and no round before a ship brought a new note, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships without a new note in 24h: 4`
- **AND** a ship counts unless the round it answered carries a fingerprint other than the round before it; the first round counts

#### Scenario: Red rounds running
- **WHEN** ABC-42's verification fails a fourth time with no other step sent between, or the Master returns it a fourth
- **THEN** nothing is sent, nobody is asked, it stops where it is, and the hover says `verify sent 3 rounds running in 24h`
