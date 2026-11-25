## MODIFIED Requirements

### Requirement: A stopped wait is woken again
A task stopped as `WAIT` SHALL be told `wait-over` again after the last wait of `infraWait.backoff`, each time the session waits again.
A report of the session that is no wait SHALL end the stop.
`outcome=wait` SHALL be refused where `infraWait.backoff` is `[]`.

#### Scenario: The wait stop keeps asking
- **WHEN** a task stopped as `WAIT` reaches the last wait of `infraWait.backoff`
- **THEN** jagt says `wait-over` once, though the task is stopped
- **AND** where the session waits again, the next wake is due after the same wait

#### Scenario: Waiting is switched off
- **WHEN** `infraWait.backoff` is `[]` and a session reports `outcome=wait`
- **THEN** the report is refused naming `outcome`, and the task keeps its status
#### Scenario: The session goes on
- **WHEN** a task stopped as `WAIT` has its session report `IN_PROGRESS` with no wait
- **THEN** the stop is voided and the log says `stop voided by read` once, with the task, the trigger and `read=session report`
