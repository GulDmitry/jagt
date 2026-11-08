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
- **AND** a ship counts unless the round it answered carries a fingerprint other than the round before it
- **AND** a ship answering no round counts only when pressed on the tree of the ship before; a first ship or a changed tree is new work
- **AND** nothing is pressed on the tree the last ship was pressed on, untracked paths included; a changed or unreadable one is due
- **AND** after three such ships, once !571 merged, a fourth is refused with no step recorded, so it never counts and nothing stops

#### Scenario: Ships of new work
- **WHEN** ABC-42 shipped four times today, each on a tree other than the ship before, and no round answered any of them
- **THEN** the Master ships again and nothing stops

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
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`StopGuard`), but the triage read, which
writes only the stop's hover. A job's `Watch` admits one only where it names the stop kind it ends.

#### Scenario: A nudge is left unsent while a task is stopped
- **WHEN** ABC-42 stands stopped with jagt's nudge unsent in its window
- **THEN** nothing presses Enter until the lift; the merged-request and ticket reads still run

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`, but a `deploy` lifts it once `DEPLOYED`
- **AND** a deploy any other origin presses is refused and lifts nothing

### Requirement: In act the Master decides a stop for a suspected loop
With `roles.master.mode: act` and `lift` not in `mine`, a STALLED, SHIPS, ROUNDS, PACE or UNCHANGED_FAILURE stop SHALL be asked of the Master once
(`MasterStopJob`). Its first line decides: `lift: <reason>` lifts it, and `return: <instruction>` lifts it and tells the session the instruction as
an answer. `escalate: <reason>` lifts it and starts the session one model tier up
([a stop lifts on the next tier](#requirement-a-stop-lifts-on-the-next-tier)), unless `escalate` is in `mine`.
`human: <why>`, any other line or no answer leaves it yours. MISS,
QA_FAILED and PAUSE stay yours, and the stops a read ends end on it.

#### Scenario: A looping task goes on
- **WHEN** ABC-42 stops on ROUNDS in `act` and the Master answers `lift: each round answered a new report`
- **THEN** the stop is lifted as the Master's and its count starts anew

#### Scenario: The Master lifts on the next tier
- **WHEN** it answers `escalate: sonnet looped on the same fixture` and `agent.tiers` names a model above
- **THEN** the stop is lifted and the session starts again one tier up

#### Scenario: The Master returns the task
- **WHEN** it answers `return: fix the fixture, not the test` and the task is not waiting on a plan
- **THEN** the stop is lifted and the session is told the instruction as an answer

#### Scenario: The stop stays yours
- **WHEN** it answers `human: the tree is yours to judge`, answers nothing usable, or the stop is a PAUSE or a MISS
- **THEN** the card keeps `lift the stop` and the Master is not asked about that stop again

#### Scenario: One lift between yours
- **WHEN** the Master lifted ABC-42's stop and the task stops again before your lift or deploy
- **THEN** that stop is yours: the Master lifts one stop per task between your lifts or deploys
- **AND** your lift or deploy restores the Master's one, a deploy too where no stop stands

#### Scenario: The Master holds the stop
- **WHEN** ABC-42 stops on ROUNDS in `act` and the Master was not yet asked
- **THEN** the card is the Master's: `master review`, no badge, `lift` still offered, and nobody is pinged or notified of the stop
- **AND** once the Master was asked and left it, the card is yours: `lift the stop`

#### Scenario: A task the Master left
- **WHEN** the Master left ABC-42 to you, or read its round, and the task stops
- **THEN** the stop is yours and the Master is not asked

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

### Requirement: You pause a started task
`pause` SHALL stop a started task for you to look at: nothing of jagt runs on it, reads included, until your `lift`.
It is offered from IN_PROGRESS on, never to a stopped task, and is the human's in every Master mode.
You may say it to the Master instead: its `pause_task` pauses the task on your word alone, with your reason on the stop,
under the same offer. The card carries the stop mark. A `deploy` you press lifts it as it lifts any stop. Nothing else ends it.

#### Scenario: A paused task is left alone
- **WHEN** you press `pause` on ABC-42 at IN_PROGRESS
- **THEN** no job reads or moves it and nothing is relayed into its session, which keeps running, until you press `lift`
- **AND** the stop is neither logged as a warning nor notified

#### Scenario: Where pause is offered
- **WHEN** ABC-42 is NEW, PLAN_PENDING, DONE or already stopped
- **THEN** `pause` is not offered

#### Scenario: The Master cannot press pause
- **WHEN** the Master presses `pause`
- **THEN** it is refused

#### Scenario: The Master pauses on your word
- **WHEN** you tell the Master to pause ABC-42 at IN_PROGRESS "until the managers answer"
- **THEN** its `pause_task` stops ABC-42 as your `pause` does, the stop reading "until the managers answer"
- **AND** where `pause` is not offered, `pause_task` is refused and nothing is stopped

### Requirement: Triage reads a stop onto its hover
With `roles.triage.enabled` set, a stop SHALL be read once by the `triage` role (`StopTriagePanel`) through the Master's reader, its
spend the Master review's. Its answer SHALL be a line of the stop's hover, `triage reads: <who> — <class> (<family>), lift looks <safe|unsafe>`:
`<who>` one of `session`, `master`, `case`, `jagt` or `none`, `<family>` a letter A to M of `docs/cases.md` or `unknown`. The read
SHALL press, send, notify and write nothing, to git or the code host included.

#### Scenario: A stop is triaged
- **WHEN** `roles.triage.enabled` is set and ABC-42 stops
- **THEN** the stop is read once, its hover gains a `triage reads: …` line, and the log says `stop triaged`

#### Scenario: Triage is unset
- **WHEN** `roles.triage.enabled` is not set and ABC-42 stops
- **THEN** nothing is read and the hover is the stop's own

#### Scenario: A lift ends the read
- **WHEN** you lift ABC-42's stop and it stops again
- **THEN** the line is gone with the first stop, and the second is read afresh

#### Scenario: A stop someone else holds
- **WHEN** ABC-42's stop stands while the task is paused or the Master holds the stop
- **THEN** it is not read until the Master leaves it

#### Scenario: The read fails
- **WHEN** the read of ABC-42's stop fails
- **THEN** the hover says `triage could not read: <cause>` and that stop is not read again

#### Scenario: Whatever triage reads, nothing moves
- **WHEN** triage reads ABC-42's stop as `lift safe`
- **THEN** nothing is pressed, notified or written to git or the code host, and the card still asks `lift the stop`

### Requirement: A stop lifts on the next tier
`escalate` SHALL lift a STALLED, SHIPS, ROUNDS, UNCHANGED_FAILURE or QA_FAILED stop.
The session SHALL start again on the next model of `agent.tiers`, the work carried over.
It is offered only while a model stands above the one the session runs on.
A human presses it, or the Master in `act` where `mine` does not name it; nothing escalates unasked.

#### Scenario: A looping task is lifted on the next tier
- **WHEN** ABC-42 stops on ROUNDS while its session runs on `sonnet` and `agent.tiers` is `[sonnet, opus]`, and you press `escalate`
- **THEN** the stop is lifted, its count starts anew, and the session starts again on `opus`
- **AND** the title hover reads `session runs a model tier up, on opus`

#### Scenario: Nothing above to move to
- **WHEN** ABC-42's session already runs on the last tier, or the stop is a PAUSE, CHECKS, INFRA, PACE or MISS one, or none stands
- **THEN** `escalate` is not offered

#### Scenario: The tier is never sticky
- **WHEN** ABC-42 runs on `opus` after `escalate`, and its session later starts again, or you press `lift` on its next stop
- **THEN** the next start is on `sonnet`, and the record names no higher tier as running
