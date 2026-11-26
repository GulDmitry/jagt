## MODIFIED Requirements

### Requirement: A stopped task is rung on the board
Each stop SHALL carry a kind, `stop.kind`. `PAUSE` is yours, `GUARD` is jagt's own loop guards, `BROKEN` a failed check or outside condition.
A `GUARD` stop SHALL wear the `■` badge and the ring, its move line `lift the stop` and its short reason.
A `BROKEN` stop SHALL wear the `!` problem badge, no ring. The hover SHALL carry the evidence, the advice and the UTC time.
#### Scenario: A stopped task says why
- **WHEN** ABC-42 is stopped on SHIPS
- **THEN** its card reads `lift the stop — ships with no new note` on the `■` stop badge, its status ringed
- **AND** the hover shows the evidence, its trigger's advice, and when it stopped
#### Scenario: A failed check is not a jagt stop
- **WHEN** ABC-42 is stopped on CHECKS
- **THEN** its badge is the `!` problem badge and its status carries no ring
#### Scenario: A problem and a plan look different
- **WHEN** a run fails on ABC-42 and ABC-43 has a plan waiting for you
- **THEN** ABC-42 wears the `!` problem badge and ABC-43 the plain required badge, each with its own legend row

### Requirement: You pause a started task
`pause` SHALL stop a started task for you to look at: nothing of jagt runs on it, reads included, until your `lift`.
It is offered from IN_PROGRESS on, never to a stopped task, and is the human's in every Master mode.
The Master's `pause_task` pauses on your word alone, under the same offer.
The card reads `you paused` and `lift your pause`, never `jagt stopped`. A `deploy` you press lifts it; nothing else ends it.
#### Scenario: A paused task is left alone
- **WHEN** you press `pause` on ABC-42 at IN_PROGRESS
- **THEN** no job reads or moves it and nothing is relayed into its session, which keeps running, until you press `lift`
- **AND** the stop is neither logged as a warning nor notified
#### Scenario: A paused task says it is yours
- **WHEN** you press `pause` on ABC-42 at IN_PROGRESS
- **THEN** its card reads `you can lift the pause` on the `‖` badge, its hover `you paused this task; lift your pause to let jagt move it again`
- **AND** its status reads `‖ paused` with no ring, the header does not count it, its history ends with that step, and no word says `jagt stopped`
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
