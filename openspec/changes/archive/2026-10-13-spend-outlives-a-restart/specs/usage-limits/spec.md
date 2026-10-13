## ADDED Requirements

### Requirement: jagt's own spend outlives a restart
Every metered call SHALL add to `usage-by-day.jsonl` beside `state.json`, one line per period and kind (`UsageLedger`):
a day for 90 days, then its month for 24 months, then its year, folded on every write so the file stays bounded.
It adds no board mark.

#### Scenario: A day ages past ninety
- **WHEN** a call is written after a day turned 90 days old
- **THEN** that day's line folds into its month's

#### Scenario: A line jagt cannot parse
- **WHEN** a line no longer parses
- **THEN** it is written back as it was, never dropped
