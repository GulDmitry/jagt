## MODIFIED Requirements

### Requirement: A stopped task is rung on the board
Each stop SHALL carry a kind, `stop.kind`. `PAUSE` is yours, `GUARD` is jagt's own loop guards and any stop only your lift ends, `BROKEN` a failed check or outside condition a read ends, its advice never to lift.
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
#### Scenario: A failed acceptance check is lifted like a guard
- **WHEN** ABC-42 is stopped on ACCEPTANCE_FAILED
- **THEN** its card reads `lift the stop` on the `■` stop badge, its status ringed
