## MODIFIED Requirements

### Requirement: The editor verb is `code`
The verb opening a task's worktrees in the configured editor SHALL be `code`, its MCP tool `open_code`, its capability `capability/code`.
The retired spelling `ide` still answers, unadvertised (`TaskAction.RENAMED`). It launches the external editor (`editorCommand`), and with none
configured launches nothing and says the viewer holds the code. Pressed on the board it selects the Code tab.
#### Scenario: The old spelling is typed
- **WHEN** the human types `ide ABC-42`
- **THEN** it runs `code ABC-42`, and the card's button reads `Code`
