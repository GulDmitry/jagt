## MODIFIED Requirements

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

## ADDED Requirements

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
