## MODIFIED Requirements

### Requirement: A stopped task waits for your lift
A human's `lift` or `deploy` SHALL clear a stop, and a new request replacing the one the stop was counted on SHALL too. The count starts anew.

#### Scenario: You press a verb on a stopped task
- **WHEN** you press `ship` or `deploy` on a stopped task
- **THEN** it runs, and the stop stands until you press `lift`, but a `deploy` lifts it once `DEPLOYED`
- **AND** a deploy any other origin presses is refused and lifts nothing
