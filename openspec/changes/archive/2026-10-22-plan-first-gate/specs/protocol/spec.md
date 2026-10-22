## ADDED Requirements

### Requirement: A session may declare that no plan comes first
`update_agent_status` SHALL take `outcome=no_plan` with status `IN_PROGRESS`, its message the reason; the report opens the
plan gate ([task-flow](../task-flow/spec.md)). For a task opened in `plan` mode it SHALL be a violation of `outcome`.

#### Scenario: Declared in plan mode
- **WHEN** a session of a `plan`-mode task reports `outcome=no_plan`
- **THEN** it is refused as a violation and the plan gate stays closed
