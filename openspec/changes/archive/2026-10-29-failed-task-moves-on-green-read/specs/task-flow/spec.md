## MODIFIED Requirements

### Requirement: Statuses a report cannot move
A round read MUST move only a task waiting on the host or red (`FlowRules.readLands`) and leave every other status in place, its checks
alone updated.

#### Scenario: A flaky red passes on re-run
- **WHEN** a `CI_FAILED` task's pipeline is re-run
- **THEN** a poll reading a re-run `CI_FAILED` task green with nothing unresolved moves it on as a `CI_POLLING` one, no session needed
