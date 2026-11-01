# task-stops Specification

## Purpose

What stops a task jagt suspects of looping, how a stop is shown, and what ends it.

## Requirements

### Requirement: jagt stops a task it suspects it is looping
`TaskStops` SHALL stop a task, its status kept, on any trigger since the last `lift`: `loopBreaker.stalledRounds` (3) relayed rounds running that
each brought no new note and left the same job failing; a Master ship past `loopBreaker.ships` (4) within `autoReview.windowHours` that answered a
round bringing no new note; a failed verification or probe, a not-ready plan or round, or a review round, sent back past
`loopBreaker.rounds` (3) running in 24h.

#### Scenario: Reworked plans are work
- **WHEN** ABC-42's plan is returned a fourth time and each of the three before answered a different plan
- **THEN** it is relayed and nothing stops

#### Scenario: Rounds that resolve nothing
- **WHEN** ABC-42 gets a fourth relayed round with the same newest notes and `unit-tests` failing, as the three before
- **THEN** nothing is relayed, the card asks `lift the stop`, and one notification names the rounds

#### Scenario: A new note resets the count
- **WHEN** ABC-42 shipped five times today, every round before a ship bringing a new note, and the Master would ship again
- **THEN** it ships, and nothing stops it
- **AND** a new thread or note, by anyone, restarts the `stalledRounds` count: two idle rounds, a note, two idle rounds count two

#### Scenario: Ships going round
- **WHEN** ABC-42 shipped four times today and no round before a ship brought a new note, and the Master would ship again
- **THEN** it presses nothing and the hover says `ships without a new note in 24h: 4`
- **AND** a ship counts unless the round it answered carries a fingerprint other than the round before it; the first round counts
- **AND** nothing is pressed on the tree the last ship was pressed on, untracked paths included; a changed or unreadable one is due
- **AND** after three such ships, once !571 merged, a fourth is refused with no step recorded, so it never counts and nothing stops

#### Scenario: Red rounds running
- **WHEN** ABC-42's verification fails a fourth time with no other step sent between, or the Master returns it a fourth
- **THEN** nothing is sent, nobody is asked, it stops where it is, and the hover says `verify sent 3 rounds running in 24h`

### Requirement: A round counts only while it answers the report before it
A round SHALL count only while it answers the session report the one before answered.
Every stop SHALL append a line `{at, task, alias, trigger, status, evidence}` to `stops.jsonl` beside
`state.json` (rolled at 1 MB), which `lift` leaves.

#### Scenario: A result outlives its round
- **WHEN** ABC-42's probes finish failed after you shipped it
- **THEN** nothing is relayed, reported, recorded or stopped, and the log says `verification outdated`; one on its own round is relayed as before

#### Scenario: A stop is logged
- **WHEN** ABC-42 stops
- **THEN** one line of `at`, `task`, `alias`, `trigger`, `status` and `evidence` is appended to `stops.jsonl`, which `lift` leaves

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`StopGuard`). A job's `Watch` admits one only where it
names the stop kind it ends.

#### Scenario: A nudge is left unsent while a task is stopped
- **WHEN** ABC-42 stands stopped with jagt's nudge unsent in its window
- **THEN** nothing presses Enter until the lift; the merged-request and ticket reads still run

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`, but a `deploy` lifts it once `DEPLOYED`
- **AND** a deploy any other origin presses is refused and lifts nothing

### Requirement: A stopped task is rung on the board
The board SHALL ring it `jagt stopped`, its move line `lift the stop` followed by the stop's short reason. That
line's hover SHALL carry the evidence, the trigger's advice and the stop's time in UTC.

#### Scenario: A stopped task says why
- **WHEN** ABC-42 is stopped on SHIPS
- **THEN** its card reads `lift the stop — ships with no new note`
- **AND** the hover shows the evidence, its trigger's advice, and when it stopped

### Requirement: Your lift ends the stop
A human's `lift` or `deploy` SHALL clear it, and a new
request replacing the one the stop was counted on SHALL too. The count starts anew. A stop an outside condition set (`CHECKS`, `INFRA`) SHALL also
end on a read listing the task's checks not red ([infra-wait](../infra-wait/spec.md)). Only SHIPS, STALLED, CHECKS, UNCHANGED_FAILURE and INFRA
stops are counted on a request; the others SHALL stand.

#### Scenario: A new request replaces the one the stop was counted on
- **WHEN** ABC-42 was stopped on !571 and opens !573
- **THEN** the stop lifts, the count restarts, the sweep reads !573, and the log says `stop lifted` with `cause=new request`
