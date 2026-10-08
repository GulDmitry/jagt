## MODIFIED Requirements

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: the arguments are read into the message and judged before
the tool runs. A field the message does not declare is ignored, so a CLI a version ahead is not rejected; one
misspelling a field the call left out is refused.

#### Scenario: An unknown field
- **WHEN** a call carries a field the message does not declare
- **THEN** the field is ignored and the call is judged on the rest

#### Scenario: A misspelled field
- **WHEN** a call sends `task_id` and no `taskId`
- **THEN** it is refused naming `taskId`, never run on the caller's own task
