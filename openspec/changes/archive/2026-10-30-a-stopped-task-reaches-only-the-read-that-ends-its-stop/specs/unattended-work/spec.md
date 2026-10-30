## MODIFIED Requirements

### Requirement: A stopped task waits for your lift
Nothing unattended SHALL poll, relay into, read, answer, ship or deploy a stopped task (`StopGuard`). A job's `Watch` admits one only where it
names the stop kind it ends. The board SHALL ring it `jagt stopped`, its move line `lift the stop` followed by the stop's short reason. That
line's hover SHALL carry the evidence, the trigger's advice and the stop's time in UTC. A human's `lift` or `deploy` SHALL clear it, and a new
request replacing the one the stop was counted on SHALL too. The count starts anew. A stop an outside condition set (`CHECKS`, `INFRA`) SHALL also
end on a read listing the task's checks not red ([infra-wait](../infra-wait/spec.md)). Only SHIPS, STALLED, CHECKS, UNCHANGED_FAILURE and INFRA
stops are counted on a request; the others SHALL stand.

#### Scenario: A stopped task says why
- **WHEN** ABC-42 is stopped on SHIPS
- **THEN** its card reads `lift the stop — ships with no new note`
- **AND** the hover shows the evidence, its trigger's advice, and when it stopped

#### Scenario: A nudge is left unsent while a task is stopped
- **WHEN** ABC-42 stands stopped with jagt's nudge unsent in its window
- **THEN** nothing presses Enter until the lift; the merged-request and ticket reads still run

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`, but a `deploy` lifts it once `DEPLOYED`
- **AND** a deploy any other origin presses is refused and lifts nothing

#### Scenario: A new request replaces the one the stop was counted on
- **WHEN** ABC-42 was stopped on !571 and opens !573
- **THEN** the stop lifts, the count restarts, the sweep reads !573, and the log says `stop lifted` with `cause=new request`
