## MODIFIED Requirements

### Requirement: The turn is a fact on the task
`flow/Turns.next` SHALL alone answer who holds a task's turn from its status, report, stop and the Master's mode and
`mine`; the board's owner maps it. A relay SHALL open the session's turn (`task/Turn`) and its next report close it.
Each starts a turn of its own id, which a Master's verdict answers. A step relayed again with the same facts while
that turn is open SHALL send nothing. What the Master pressed, left or failed to answer SHALL stay on the turn, so a
restart repeats none.

#### Scenario: A human presses a verb while the Master reads
- **WHEN** the Master is reading ABC-42's hand-back and you press `ship`
- **THEN** its verdict, ready, not ready or a question, is dropped and logged with both turns, whatever it says
- **AND** ABC-42 stays `SHIPPING` and its session is relayed nothing
- **AND** a verdict that stands claims its status and turn in one write, and the session is told after it, outside any lock
