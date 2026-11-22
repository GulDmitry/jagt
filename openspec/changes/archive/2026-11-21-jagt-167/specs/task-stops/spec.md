## MODIFIED Requirements

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (the chart does not offer `ship` or `deploy` to a non-human origin), but the triage read, which
writes only the stop's hover. A job's `Watch` admits one only where it names the stop kind it ends.

#### Scenario: A nudge is left unsent while a task is stopped
- **WHEN** ABC-42 stands stopped with jagt's nudge unsent in its window
- **THEN** nothing presses Enter until the lift; the merged-request and ticket reads still run

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`, but a `deploy` lifts it once `DEPLOYED`
- **AND** a deploy any other origin presses is refused and lifts nothing, saying jagt stopped it and only a human presses it until `lift`
