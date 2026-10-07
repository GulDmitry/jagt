## ADDED Requirements

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following
`jagt-master.log`, one line per Master step (`MasterFeedWindow`); closed, it SHALL come back.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns a round, ships or opens a task
- **THEN** the `master` window shows it with the task and, for an answer, the question and the decision

#### Scenario: You close the master window
- **WHEN** you close the `master` window while the Master runs
- **THEN** it comes back within seconds
