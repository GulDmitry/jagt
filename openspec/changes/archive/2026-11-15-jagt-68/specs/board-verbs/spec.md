## ADDED Requirements

### Requirement: A board-wide stop halts all work until your lift
`stop`, a `GlobalCommand` with one confirm, SHALL interrupt each working session with its runtime's interrupt, leaving it in tmux.
It sets the flag `board-stopped` beside `state.json`. While it stands no job runs, intake included, and `act` reads `judge`, `jagt.yml` untouched.
The Master in `act` may press `stop`; only the human's `unstop` ends it, and a stopped board puts the Master in `judge`.
#### Scenario: You stop the board
- **WHEN** you run `stop` and confirm while ABC-42's session works and ABC-43's waits at its prompt
- **THEN** ABC-42's session is interrupted and ABC-43's is left alone, both still in tmux
- **AND** no job or intake run starts, and the Master judges only
#### Scenario: You decline
- **WHEN** you run `stop` and decline the confirm
- **THEN** nothing is interrupted or paused
#### Scenario: A restart while stopped
- **WHEN** jagt restarts with the flag standing
- **THEN** it starts stopped, the mark shows, and no job runs
#### Scenario: You lift it
- **WHEN** you press the mark or run `unstop`
- **THEN** jobs and intake run again, the Master reads its configured mode, and sessions take their next prompt

### Requirement: The stop is one mark on the board's head
The header SHALL carry one `.chip.stalled`, its hover the time of the stop in UTC; pressing it runs `unstop`. It replaces pausing each
started task by hand and wears the colour of a stopped `act`; the legend shows it on the row of the other stopped marks.
#### Scenario: The mark
- **WHEN** the board is stopped
- **THEN** the header shows `board stopped` and every card stays readable
#### Scenario: The tab icon
- **WHEN** the board is stopped and nothing else is broken
- **THEN** the tab icon stays unmarked, for a stop the human chose is not a break
