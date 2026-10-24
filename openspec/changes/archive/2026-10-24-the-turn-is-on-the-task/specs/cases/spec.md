## ADDED Requirements

### Requirement: The turn is a fact on the task
`flow/Turns.next` SHALL alone answer who holds a task's turn from its status, its report and the Master's mode and
`mine`. A relay SHALL open the session's turn on the task (`task/Turn`: step, digest of its facts, `since`); the
session's next report SHALL close it. A step relayed again with the same facts while that turn is open SHALL send
nothing. What the Master pressed, left or failed to answer SHALL be kept on the turn, so a restart repeats none.

#### Scenario: jagt restarts after a deploy
- **WHEN** ABC-42 was deployed, its check asked and another relay followed, and jagt restarts
- **THEN** the Master presses no deploy again and the session is not told again that ABC-42 is deployed

#### Scenario: The same step twice
- **WHEN** the deploy check of ABC-42 is relayed twice before its session reports
- **THEN** the session is told once

#### Scenario: The session goes back to work while the Master reads
- **WHEN** the Master is reading ABC-42's question and its session reports in the meantime
- **THEN** the Master's answer is dropped and nothing sets ABC-42 `IN_PROGRESS`
