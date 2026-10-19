## MODIFIED Requirements

### Requirement: jagt's own spend outlives a restart
Every metered call SHALL add to `usage-by-day.jsonl` beside `state.json`, one line per period and kind (`UsageLedger`):
a day for 90 days, then its month for 24 months, then its year, folded on every write so the file stays bounded.
Each line's `usage` holds `maxCallCostUsd`, the dearest single call of that line, folded with max where the sums fold
with +; a line written before it was kept reads as 0.
It adds no board mark.

#### Scenario: A day ages past ninety
- **WHEN** a call is written after a day turned 90 days old
- **THEN** that day's line folds into its month's

#### Scenario: A line jagt cannot parse
- **WHEN** a line no longer parses
- **THEN** it is written back as it was, never dropped

#### Scenario: Two calls of one kind in a day
- **WHEN** a call costing 0.30 and one costing 0.05 are metered the same day
- **THEN** the line sums them to 0.35 and its `maxCallCostUsd` is 0.30

#### Scenario: A line written before the maximum was kept
- **WHEN** a call is metered into a line without `maxCallCostUsd`
- **THEN** the line reads and its `maxCallCostUsd` is that call's cost
