## MODIFIED Requirements

### Requirement: The stand records its timings as a trend
The server scenario SHALL write startup seconds and `/state` p95 in milliseconds, the median of five rounds of 50 calls.
Each is a `customSmallerIsBetter` series per runner label and engine, written whether or not the absolute budgets hold.
CI SHALL keep no history and run no timing gate.
`standTimingGate` SHALL compare each series with the median of its last ten history entries and fail naming the series past 200 % of that median.
A series with no history entry SHALL pass.
#### Scenario: A run on a shared runner
- **WHEN** the stand job runs on a CI runner
- **THEN** `startup <runner>/<engine>` and `state p95 <runner>/<engine>` are written and nothing compares them

#### Scenario: A slowdown
- **WHEN** a timing is over 200 % of the median of its last ten history entries
- **THEN** `standTimingGate` fails naming that series

#### Scenario: One fast outlier in the history
- **WHEN** the newest history entry is far faster than the rest and a normal run follows
- **THEN** `standTimingGate` does not fail
