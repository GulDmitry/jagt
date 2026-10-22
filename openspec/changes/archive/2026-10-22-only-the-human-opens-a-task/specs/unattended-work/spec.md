## MODIFIED Requirements

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following
`jagt-master.log`, one line per Master step (`MasterFeedWindow`). Closed, it SHALL come back within seconds; each start rolls the log into `.1`.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys or stops `act`
- **THEN** the `master` window shows it with the task, and an answer with its question and decision
