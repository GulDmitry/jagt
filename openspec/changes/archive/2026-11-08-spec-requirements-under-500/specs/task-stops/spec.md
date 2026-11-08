## MODIFIED Requirements

### Requirement: In act the Master decides a stop for a suspected loop
In `roles.master.mode: act` without `lift` in `mine`, a STALLED, SHIPS, ROUNDS, PACE or UNCHANGED_FAILURE stop SHALL be asked of the Master once
(`MasterStopJob`). Its first line decides: `lift: <reason>` lifts; `return: <instruction>` lifts and answers the session with it; `escalate: <reason>`
lifts and restarts it one tier up ([next tier](#requirement-a-stop-lifts-on-the-next-tier)) unless in `mine`. Any other line or none leaves it yours;
a stop a read ends ends on it.

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

#### Scenario: Stops the Master never decides
- **WHEN** a task stops on MISS, QA_FAILED or PAUSE in `act`
- **THEN** the stop is yours and the Master is not asked
