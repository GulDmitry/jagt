## MODIFIED Requirements

### Requirement: An open request is what the poller watches
`AutoReviewCadence.polls` SHALL watch an open request, never a status: a request exists and the task is not DONE. At
`REVIEWED`, `APPROVED` or `DEPLOYED` only a reviewer moves it on, so it SHALL be read every `maxIntervalMinutes`.

#### Scenario: Comments after hand-back
- **WHEN** comments arrive after the agent handed back
- **THEN** the next poll picks them up

#### Scenario: Threads never resolved
- **WHEN** the reviewer never resolves the threads
- **THEN** polls continue; the agent is re-briefed only when the round changed

#### Scenario: Deployed, green, unapproved
- **WHEN** a deployed task comes back green and unapproved
- **THEN** it stays `deployed`, read every `maxIntervalMinutes` from the start: a poll never moves work that went out

### Requirement: Unattended starts have a daily ceiling
At `orchestrator.maxStartsPerDay` (default 50) jagt SHALL write the dated file `starts-stopped` beside `state.json`,
separate from `act-stopped`. While it stands the Master SHALL only judge, and intake and auto-review SHALL start
nothing; a human press SHALL never be refused. It SHALL lift on the next local day, or when you press the header's
`act stopped`, which names its cause in the hover.

#### Scenario: The ceiling is reached
- **WHEN** the 50th unattended start of a local day is taken
- **THEN** the Master only judges, intake and auto-review start nothing, and `act stopped`'s hover names the 50

#### Scenario: The stop lifts
- **WHEN** the next local day begins, or you press `act stopped` while the daily stop stands
- **THEN** it is gone and starts count from zero
