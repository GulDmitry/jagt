## ADDED Requirements

### Requirement: A closed plan gate holds the flow, not only the writes
While a task's plan gate is closed the flow SHALL refuse a session's report of `REVIEW_PENDING`, `SHIPPING` or `CI_POLLING`, and the `ship` verb, naming
the plan; `PLAN_PENDING` and `IN_PROGRESS` stay reportable. A review round relayed to a task at `SHIPPING`, `REVIEW_PENDING`, `CI_POLLING` or later SHALL open the gate.

#### Scenario: Shell writes under autoplan
- **WHEN** an autoplan session reports `REVIEW_PENDING` or asks to ship while its gate is closed
- **THEN** the report or press is refused naming the plan, and the task stays where it is

#### Scenario: A round after the ship
- **WHEN** a review round is relayed to a task at `REVIEW_PENDING` whose gate is closed
- **THEN** the gate opens and the session's writes are not refused as plan first
