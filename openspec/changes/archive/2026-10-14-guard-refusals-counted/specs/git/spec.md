## ADDED Requirements

### Requirement: A refusal is counted on its task
A session line `ToolGate` refuses and a report `FlowRules.refusedReport` refuses SHALL each add one to the task's
`refusals`, per `task/Guard`. `finished.jsonl` SHALL carry the count, and `finished` SHALL sum it per kind.

#### Scenario: A push to another branch
- **WHEN** ABC-42's session runs `git push origin dev`
- **THEN** it is refused, and ABC-42's `refusals` count one `session line`

#### Scenario: The finished report
- **WHEN** the human runs `finished`
- **THEN** its last line sums the refusals of every finished task per kind
