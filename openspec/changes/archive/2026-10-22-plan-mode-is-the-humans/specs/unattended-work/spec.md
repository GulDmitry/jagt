## RENAMED Requirements

- FROM: `### Requirement: One run, one log`
- TO: `### Requirement: A restart keeps the run before`

## MODIFIED Requirements

### Requirement: A restart keeps the run before
`logback-spring.xml` SHALL drop Boot's rolling appender; `surface/ui/LogFileRoll` SHALL roll the file into `.1` before it opens.

#### Scenario: Restart
- **WHEN** jagt starts
- **THEN** the log holds this run, its `.1` the run before

### Requirement: Every Master step is seen in one window
While `master.mode` is not `off`, jagt SHALL keep one `master` window in its tmux session following
`jagt-master.log`, one line per Master step (`MasterFeedWindow`). Closed, it SHALL come back within seconds; each start rolls the log into `.1`.

#### Scenario: You want to know why a window opened or closed
- **WHEN** the Master answers, asks, returns, starts, ships, deploys, opens a task or stops `act`
- **THEN** the `master` window shows it with the task, and an answer with its question and decision
