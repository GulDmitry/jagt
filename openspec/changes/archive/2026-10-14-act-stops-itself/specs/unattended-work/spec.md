## ADDED Requirements

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following
`jagt-master.log`, one line per Master step (`MasterFeedWindow`); closed, it SHALL come back within seconds.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys, opens a task or stops `act`
- **THEN** the `master` window shows it with the task, and an answer with its question and decision
