## ADDED Requirements

### Requirement: The stand records its timings as a trend
The server scenario SHALL write startup seconds and `/state` p95 in milliseconds, the median of five rounds of 50 calls.
Each is a `customSmallerIsBetter` series per runner label and engine, written whether or not the absolute budgets hold.
The CI stand job SHALL compare them with the history kept in the Actions cache, never a pushed branch.
It SHALL fail naming the series when one passes 200 % of its history.
#### Scenario: A run on a shared runner
- **WHEN** the stand job runs `-Pbudgets=false` on a runner
- **THEN** `startup <runner>/<engine>` and `state p95 <runner>/<engine>` are written and compared with the history

#### Scenario: A slowdown
- **WHEN** a timing is over 200 % of the stored history
- **THEN** the stand job fails naming that series
