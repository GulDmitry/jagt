## MODIFIED Requirements

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
