## MODIFIED Requirements

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`StopGuard`). The board
SHALL ring it `jagt stopped`, its hover the evidence. Only a human's `lift`, or a new request replacing the one the stop
was counted on, SHALL clear it and count anew. A stop counted on no request SHALL stand.

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`

#### Scenario: A new request replaces the one the stop was counted on
- **WHEN** ABC-42 was stopped on !571 and opens !573
- **THEN** the stop lifts, the count restarts, and the auto-review sweep reads !573
- **AND** the log says `stop lifted` with `cause=new request`

#### Scenario: A stop counted on no request
- **WHEN** ABC-42 was stopped by a failed verification and opens !573
- **THEN** the stop stands until you press `lift`
