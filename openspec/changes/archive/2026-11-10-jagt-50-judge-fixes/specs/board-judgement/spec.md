## MODIFIED Requirements

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

### Requirement: Orphaned worktrees are listed, each with its own delete verb
`worktrees` SHALL be a report listing each directory the orphan scan finds. Each shows its project, its copied secret
files and the typed verb that deletes it. `delete-worktree <name>` SHALL delete the one listed directory called `<name>`
with all it holds, and refuse any name the scan does not list. A directory git still lists as a worktree is removed
with `git worktree remove --force` in its repository; any other is deleted as a directory. No verb deletes more than
one, and none prunes. The Master holds the verb as a human does. The orphan ping points to `worktrees`.

#### Scenario: Listing
- **WHEN** you type `worktrees` and `ABC-40-demo` holds two copied secret files and no task owns it
- **THEN** the report shows `ABC-40-demo`, its project, `2 copied secret file(s)` and `delete-worktree ABC-40-demo`

#### Scenario: Deleting one
- **WHEN** you type `delete-worktree ABC-40-demo`
- **THEN** that directory is gone and no other

#### Scenario: A registered orphan
- **WHEN** `ABC-40-demo` is still in its repository's worktree list and you type `delete-worktree ABC-40-demo`
- **THEN** git removes that one worktree and its registration, and no other worktree is touched

#### Scenario: The Master deletes one
- **WHEN** the Master in `act` runs `delete-worktree ABC-40-demo` for a listed orphan
- **THEN** it is deleted as for a human

#### Scenario: A name that is no orphan
- **WHEN** you type `delete-worktree` with the name of a task's live worktree or of any other directory
- **THEN** it is refused, saying `worktrees` lists them, and nothing is deleted
