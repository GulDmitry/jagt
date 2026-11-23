## MODIFIED Requirements

### Requirement: The stand records its timings as a trend
The server scenario SHALL write startup seconds and `/state` p95 in milliseconds, the median of five rounds of 50 calls.
Each is a `customSmallerIsBetter` series per runner label and engine, written whether or not the absolute budgets hold.
The CI stand job SHALL keep the history in the Actions cache, never a pushed branch.
It SHALL compare each series with the median of its last ten history entries and fail naming the series past 200 % of that median.
A series with no history entry SHALL pass.
#### Scenario: A run on a shared runner
- **WHEN** the stand job runs without `-Pbudgets=true` on a runner
- **THEN** `startup <runner>/<engine>` and `state p95 <runner>/<engine>` are written and compared with the median of the history

#### Scenario: A slowdown
- **WHEN** a timing is over 200 % of the median of its last ten history entries
- **THEN** the stand job fails naming that series

#### Scenario: One fast outlier in the history
- **WHEN** the newest history entry is far faster than the rest and a normal run follows
- **THEN** the stand job does not fail
