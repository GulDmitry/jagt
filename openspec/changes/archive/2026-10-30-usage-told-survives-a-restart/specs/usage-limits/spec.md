## MODIFIED Requirements

### Requirement: A limit is told once per period, never gated
The job SHALL send a `USAGE` banner (`job/UsageAlerts`) once per window period and SHALL never refuse or slow a task.

#### Scenario: A window fills
- **WHEN** the weekly window reaches 96% with thresholds 80 and 95
- **THEN** one banner says `week window at 95%`, and no later poll before its reset, nor a restart, repeats it

#### Scenario: Money starts flowing
- **WHEN** a window reads 100% while paid usage is enabled, or paid usage rises
- **THEN** one banner says `paid usage started`, with what is spent of the limit

#### Scenario: Paid usage nears its limit
- **WHEN** paid usage reaches a `paidThresholds` share of its monthly limit
- **THEN** one banner says so, until it reads below that share again


