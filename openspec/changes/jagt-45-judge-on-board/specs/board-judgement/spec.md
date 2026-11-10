## ADDED Requirements

### Requirement: A failed check's stop shows its output
A stop set because a check failed (`UNCHANGED_FAILURE`, `ACCEPTANCE_FAILED`) SHALL carry that check's output as its
evidence, which the card shows with the stop. Evidence past `TaskStops.MAX_EVIDENCE` characters is cut and ends with
`[cut]`, so a reader knows the rest was not kept.

#### Scenario: The same failure again
- **WHEN** a hand-back stops as `UNCHANGED_FAILURE` because the tree is the one its verification already failed on
- **THEN** the stop's evidence holds the failing command's output

#### Scenario: Output too long to keep
- **WHEN** a stop's evidence is longer than the cap
- **THEN** it is kept up to the cap and ends with `[cut]`

### Requirement: Trust git shows what it adopts
Where a task's git config or hooks differ from what was trusted, the Diff tab SHALL show one more block after the
repositories, under the muted header `git config and hooks · trusted`: an added or changed entry's current content,
a removed one's name. It reads on request and stores nothing. The parked card's hint says where to read it.

#### Scenario: A session sets a program
- **WHEN** a session runs `git config core.fsmonitor /tmp/x` and you open the task's Diff tab
- **THEN** the block names `core.fsmonitor in <repo>/.git/config added` and shows `/tmp/x` as an added line

#### Scenario: A secret
- **WHEN** a changed remote URL carries `user:pw@`, or a key names a password, token, secret or credential
- **THEN** the URL shows `***@` in place of the user part and the key's value shows `(hidden)`

#### Scenario: Too long to show
- **WHEN** a value or hook is longer than `GitStates.MAX_VALUE`, or the block longer than its cap
- **THEN** it is cut and flagged

#### Scenario: Nothing differs
- **WHEN** git obeys what was trusted, or nothing was recorded
- **THEN** the tab shows no such block

### Requirement: The log has a tail on the board
`tail` SHALL be a report: the last `LogTail.LINES` lines of jagt's own log, one readable line each (time, level,
message, fields), a line past `LogTail.MAX_LINE` characters cut and flagged. It reads on request and stores nothing.

#### Scenario: Reading what failed
- **WHEN** you type `tail`
- **THEN** a dialog shows the newest lines of the log, oldest first, each as `<time> <level> <message> <field>=<value>`

#### Scenario: No log
- **WHEN** the log file does not exist or is empty
- **THEN** the report says where the log would be and that it holds nothing yet
