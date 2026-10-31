## ADDED Requirements

### Requirement: QA holds the turn while it reads
A QA run SHALL hold the task's turn from its start until it answers, so the board shows the agent at work.
No Master answers or deploys meanwhile. A stopped task is the human's. A check waiting on its environment holds nobody.
A turn left open and unanswered by a restart SHALL run the same check again on the same sha, and nothing is asked twice.

#### Scenario: A check reads
- **WHEN** QA reads ABC-42 on its deploy
- **THEN** QA holds the turn until the run answers, then nobody does

#### Scenario: A restart in the middle of a read
- **WHEN** jagt restarts with QA's turn on ABC-42 open and unanswered
- **THEN** the same check runs again on the same sha
