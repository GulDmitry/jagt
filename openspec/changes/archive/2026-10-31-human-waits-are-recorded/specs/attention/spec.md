## ADDED Requirements

### Requirement: The human's waits are recorded
Each task SHALL keep one interval per stretch its turn rested with the human (`Turns.next` naming HUMAN): start, end and
why (`QUESTION`, `PERMISSION`, `STOP`, `VERDICT` where the Master read the plan or round and left it in `judge`, `STATUS` where the status itself is the human's), the newest 50 at most, and the
finished record SHALL carry them. An interval stands open in `state.json` until a state write shows the turn gone; a
restart keeps it open and its first sweep closes it only where the turn has left the human. A closed one logs
`human wait closed` with task, why, since, until and millis. No board mark shows them.

#### Scenario: A question is answered
- **WHEN** a session asks and later works on
- **THEN** one `QUESTION` interval runs from the ask to the answer

#### Scenario: A restart in a wait
- **WHEN** jagt restarts while the human holds the turn
- **THEN** the interval stays open and no second one starts
