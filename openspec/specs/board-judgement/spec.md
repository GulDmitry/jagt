# board-judgement Specification

## Purpose
What the board shows of a judged task's evidence, adopted trust, its log tail and orphaned worktrees.

## Requirements

### Requirement: A failed check's stop shows its output
A stop set because a check failed (`UNCHANGED_FAILURE`, `ACCEPTANCE_FAILED`) SHALL carry that check's output as its
evidence, which the card shows with the stop. An `UNCHANGED_FAILURE` stop carries the output of the run that failed,
kept when that round was told; the check is never run again for it. Evidence past `TaskStops.MAX_EVIDENCE` characters
is cut and ends with `[cut]`, so a reader knows the rest was not kept.

#### Scenario: The same failure again
- **WHEN** a hand-back stops as `UNCHANGED_FAILURE` because the tree is the one its verification already failed on
- **THEN** the stop's evidence holds the output of the run that failed
- **AND** the verify command is not run a second time

#### Scenario: Output too long to keep
- **WHEN** a stop's evidence is longer than the cap
- **THEN** it is kept up to the cap and ends with `[cut]`

### Requirement: Trust git shows what it adopts
Where a task's git config or hooks differ from what was trusted, the Diff tab SHALL show one more block after the
repositories. Its header is muted: `git config and hooks · trusted`. It holds an added or changed entry's current
content, a removed one's name. It reads on request and stores nothing. The parked card's hint says where to read it.

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

### Requirement: Orphaned worktrees are listed with their delete verb
`worktrees` SHALL be a report listing each directory the orphan scan finds. Each shows its project, its copied secret
files and the typed verb that deletes it. `delete-worktree <name>` SHALL delete the one listed directory called `<name>`
with all it holds, and refuse any name the scan does not list. No verb deletes more than
one, and none prunes. The Master holds the verb as a human does. The orphan ping points to `worktrees`.

#### Scenario: Listing
- **WHEN** you type `worktrees` and `ABC-40-demo` holds two copied secret files and no task owns it
- **THEN** the report shows `ABC-40-demo`, its project, `2 copied secret file(s)` and `delete-worktree ABC-40-demo`

#### Scenario: Deleting one
- **WHEN** you type `delete-worktree ABC-40-demo`
- **THEN** that directory is gone and no other

#### Scenario: The Master deletes one
- **WHEN** the Master in `act` runs `delete-worktree ABC-40-demo` for a listed orphan
- **THEN** it is deleted as for a human

#### Scenario: A name that is no orphan
- **WHEN** you type `delete-worktree` with the name of a task's live worktree or of any other directory
- **THEN** it is refused, saying `worktrees` lists them, and nothing is deleted

### Requirement: A registered orphan is removed through git
A directory git still lists as a worktree SHALL be removed with `git worktree remove --force` in its repository;
any other is deleted as a directory.

#### Scenario: A registered orphan
- **WHEN** `ABC-40-demo` is still in its repository's worktree list and you type `delete-worktree ABC-40-demo`
- **THEN** git removes that one worktree and its registration, and no other worktree is touched
