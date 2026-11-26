## MODIFIED Requirements

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status a session reports over MCP, refused unless `FlowRules.refusedReport` allows it. `REVIEWED` and `APPROVED`
MUST land only through `FlowReports.read`, the host's round read. The refusal MUST own the reason the agent acts on: no task talks itself onto a
shared branch, out of one, closed, past its review, or off a closed plan gate an outside system set ([task-start](../task-start/spec.md)).

#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason
- **AND** a session reporting `APPROVED` is refused: only the round read lands it

