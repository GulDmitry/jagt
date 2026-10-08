## MODIFIED Requirements

### Requirement: One table owns the life of a task
`flow/FlowRules` SHALL hold, in one compiler-checked Java file, which statuses allow which action and what each
outcome leads to. Its guard MUST read `flow/Facts`: an open request, a live deploy, and a liveness probe the projection
passes as "no" (a spawn per row).

#### Scenario: An action is asked
- **WHEN** an action is asked
- **THEN** `FlowRules` allows or refuses it
