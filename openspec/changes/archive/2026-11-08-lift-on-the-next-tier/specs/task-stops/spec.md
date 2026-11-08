## ADDED Requirements

### Requirement: A stop lifts on the next tier
`escalate` SHALL lift a STALLED, SHIPS, ROUNDS, UNCHANGED_FAILURE or QA_FAILED stop.
The session SHALL start again on the next model of `agent.tiers`, the work carried over.
It is offered only while a model stands above the one the session runs on.
Only a human presses it; nothing escalates unasked.

#### Scenario: A looping task is lifted on the next tier
- **WHEN** ABC-42 stops on ROUNDS while its session runs on `sonnet` and `agent.tiers` is `[sonnet, opus]`, and you press `escalate`
- **THEN** the stop is lifted, its count starts anew, and the session starts again on `opus`
- **AND** the title hover reads `session runs a model tier up, on opus`

#### Scenario: Nothing above to move to
- **WHEN** ABC-42's session already runs on the last tier, or the stop is a PAUSE, CHECKS, INFRA, PACE or MISS one, or none stands
- **THEN** `escalate` is not offered

#### Scenario: The tier is never sticky
- **WHEN** ABC-42 runs on `opus` after `escalate`, and its session later starts again, or you press `lift` on its next stop
- **THEN** the next start is on `sonnet`, and the record names no higher tier as running
