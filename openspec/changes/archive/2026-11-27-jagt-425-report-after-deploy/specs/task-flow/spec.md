## MODIFIED Requirements

### Requirement: Door one runs an action
`flow/FlowEngine.run` SHALL check the rules, run the action's `flow/capability/TaskCapability`, write the table's status for `capability/Outcome`.

#### Scenario: A verb runs
- **WHEN** an allowed action finishes
- **THEN** its outcome sets the status

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status a session reports over MCP, refused unless `FlowRules.refusedReport` allows it. `REVIEWED` and `APPROVED`
MUST land only through `FlowReports.read`, the host's round read. The refusal MUST own the reason the agent acts on: no task talks itself onto a
shared branch, out of one, closed, past its review. An outside system's plan gate is held the same way ([task-start](../task-start/spec.md)).

#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason
- **AND** a session reporting `APPROVED` is refused: only the round read lands it
- **AND** a `DEPLOYED` task's session reporting `SHIPPING` lands: it keeps working after the deploy, and a refusal errors every call
