## ADDED Requirements

### Requirement: Unattended starts have a daily ceiling
jagt SHALL count the starts it makes unasked per local day, in memory, and the count SHALL begin at zero on a restart.
A start is a Master move in `act` that starts work (`MasterPress`, `ConflictHandOff`), a tracker intake start
(`IntakeJob`) or an auto-review sweep (`AutoReviewScheduler`). Closing a ticket, a Master judge read and a ticket
prefetch SHALL NOT count. At `orchestrator.maxStartsPerDay` (default 50) jagt SHALL write the dated file
`starts-stopped` beside `state.json`, separate from `act-stopped`. While it stands the Master SHALL only judge, and
intake and auto-review SHALL start nothing; a human press SHALL never be refused. It SHALL lift on the next local
day, or when you press the header's `act stopped`, which names its cause in the hover.

#### Scenario: The ceiling is reached
- **WHEN** the 50th unattended start of a local day is taken
- **THEN** the Master only judges, intake and auto-review start nothing, and the header shows `act stopped`
  whose hover names the 50

#### Scenario: A new day
- **WHEN** the local day after the stop begins
- **THEN** the stop is gone without a press and starts count from zero

#### Scenario: You press the chip
- **WHEN** you press `act stopped` while the daily stop stands
- **THEN** it lifts and the count begins at zero

#### Scenario: A human press
- **WHEN** you press a verb while the daily stop stands
- **THEN** it runs
