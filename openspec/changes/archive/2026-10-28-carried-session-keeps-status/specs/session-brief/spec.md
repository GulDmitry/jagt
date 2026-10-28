## ADDED Requirements

### Requirement: A carried task keeps its status until a change begins
The `start` step SHALL order IN_PROGRESS only for a task new to the session.
A task carried from a previous session SHALL keep the status the board shows while the session only answers.
It SHALL end its turn without a report.
A session beginning a change SHALL report IN_PROGRESS and hand back as the `context` rules say.

#### Scenario: A question on a reviewed task
- **WHEN** ABC-42 is REVIEWED and its fresh session is only asked a question
- **THEN** the session answers and ends its turn without a status report
- **AND** the board still shows REVIEWED

#### Scenario: A change asked on a reviewed task
- **WHEN** ABC-42 is REVIEWED and the human asks its fresh session for a change
- **THEN** the session reports IN_PROGRESS, makes the change and hands back REVIEW_PENDING

#### Scenario: A new task
- **WHEN** ABC-43 is new to its session
- **THEN** the session reports IN_PROGRESS first
