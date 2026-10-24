## MODIFIED Requirements

### Requirement: The history names who asked
Each step of a task's status history SHALL name who asked for it (`task/ActionOrigin`), the opening step what opened
the task: the board, the palette, an MCP call or the tracker. A step jagt took unasked names nobody. A step whose asker
gave a reason SHALL show it in the step's hover alone. The history holds 50 steps. Past that the oldest after the
opening step drop; the opening step stays. Time held per owner leaves out the gap after it.

#### Scenario: Opened twice
- **WHEN** ABC-42 is opened on the board and ABC-43 by an `initialize_task` call
- **THEN** ABC-42's first step reads `board` and ABC-43's `mcp`

#### Scenario: Why a step was taken
- **WHEN** ABC-42's session hands back with `why` "work done, tests green, ready for review"
- **THEN** hovering its REVIEW_PENDING step reads that sentence, and neither the card nor the step's row shows it

#### Scenario: A long task
- **WHEN** ABC-42 opened on the board takes a 60th step
- **THEN** its first step still reads `board`, the next 49 are the most recent, and no status is charged the time between
