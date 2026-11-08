## RENAMED Requirements

- FROM: `### Requirement: A verb a human keeps is named in the table`
- TO: `### Requirement: The Master in act presses every verb but those mine names`

## MODIFIED Requirements

### Requirement: The Master in act presses every verb but those mine names
The Master SHALL press every `TaskAction` where `roles.master.mode` is `act` and a Master case allows it ([master](../master/spec.md)); only
`roles.master.mine` keeps a verb from it (`CaseDoor.presses`), no table of verbs does.

#### Scenario: Closing a task
- **WHEN** the Master presses `done` and `mine` does not name it
- **THEN** the door admits it as a human's press, held to a Master case
