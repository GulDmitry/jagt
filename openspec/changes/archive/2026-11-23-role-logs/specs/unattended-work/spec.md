## MODIFIED Requirements

### Requirement: Every Master step is seen in the role's log on the board
The Master's steps SHALL be lines of its feed, shown by [role-logs](../board/spec.md#requirement-every-enabled-model-roles-log-opens-in-the-boards-pane);
jagt SHALL open no tmux window for them. Each start rolls `jagt-master.log` into `.1`.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys or stops `act`
- **THEN** the Master's log shows it with the task, and an answer with its question and decision
