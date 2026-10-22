## MODIFIED Requirements

### Requirement: A task's work stays in the task
Work a task needs SHALL be done in it, on its own branch, and the Master SHALL open no task: a `do` line in its answer
opens nothing, the session told only you open a task. A repository the task lacks is a question for you.

#### Scenario: The Master's answer holds a `do` line
- **WHEN** in `act` it answers a session with `do proj drop the old keys from main`
- **THEN** no task opens; the session does that work on its own branch
