## ADDED Requirements

### Requirement: In act the Master decides a stop for a suspected loop
With `roles.master.mode: act` and `lift` not in `mine`, a STALLED, SHIPS, ROUNDS, PACE or UNCHANGED_FAILURE stop SHALL be asked of the Master once
(`MasterStopJob`). Its first line decides: `lift: <reason>` lifts it, and `return: <instruction>` lifts it and tells the session the instruction as
an answer. `human: <why>`, any other line or no answer leaves it yours. Between your lifts or deploys the Master SHALL lift one stop per task. MISS,
QA_FAILED and PAUSE stay yours, and the stops a read ends end on it.

#### Scenario: A looping task goes on
- **WHEN** ABC-42 stops on ROUNDS in `act` and the Master answers `lift: each round answered a new report`
- **THEN** the stop is lifted as the Master's and its count starts anew

#### Scenario: The Master returns the task
- **WHEN** it answers `return: fix the fixture, not the test` and the task is not waiting on a plan
- **THEN** the stop is lifted and the session is told the instruction as an answer

#### Scenario: The stop stays yours
- **WHEN** it answers `human: the tree is yours to judge`, answers nothing usable, or the stop is a PAUSE or a MISS
- **THEN** the card keeps `lift the stop` and the Master is not asked about that stop again

#### Scenario: One lift between yours
- **WHEN** the Master lifted ABC-42's stop and the task stops again before your lift or deploy
- **THEN** that stop is yours, and your lift or deploy restores the Master's one
