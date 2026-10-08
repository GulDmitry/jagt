## MODIFIED Requirements

### Requirement: The shape and the tool are declared once
`protocol/Schema` SHALL render what a caller is given out of the fields a message declares. The JSON a CLI reads and
the rules judging it so cannot disagree. An enum comes from whatever enumerates it (`FlowRules.reportable`),
never a list beside it. `McpToolRegistry` has one way to declare a tool, taking a message class,
so a tool skipping validation does not compile.

#### Scenario: A status is added
- **WHEN** a session may report a new status
- **THEN** the schema offers it with no other edit
