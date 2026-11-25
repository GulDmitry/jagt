## ADDED Requirements

### Requirement: A round with nothing new says so on its verb
A `sweep` that reads the round unchanged, or still waiting on the reviewer or the checks, SHALL answer as work not done, and the board SHALL draw
that verb's own button with a dashed muted edge, its hover carrying the answer. The mark ends when the task's status moves or the verb is pressed again.
#### Scenario: Nothing new
- **WHEN** the human presses Check review on ABC-42 and the round reads unchanged
- **THEN** the button wears the dashed edge and the toast says so
#### Scenario: The card moves
- **WHEN** ABC-42's status changes, or the human presses Check review again
- **THEN** the dashed edge is gone
