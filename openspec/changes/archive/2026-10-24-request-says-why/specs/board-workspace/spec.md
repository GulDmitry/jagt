## MODIFIED Requirements

### Requirement: The history names who asked
Each step of a task's status history SHALL name who asked for it (`task/ActionOrigin`), the opening step what opened
the task: the board, the palette, an MCP call or the tracker. A step jagt took unasked names nobody. A step whose asker
gave a reason SHALL show it in the step's hover alone.

#### Scenario: Opened twice
- **WHEN** ABC-42 is opened on the board and ABC-43 by an `initialize_task` call
- **THEN** ABC-42's first step reads `board` and ABC-43's `mcp`

#### Scenario: Why a step was taken
- **WHEN** ABC-42's session hands back with `why` "work done, tests green, ready for review"
- **THEN** hovering its REVIEW_PENDING step reads that sentence, and neither the card nor the step's row shows it
