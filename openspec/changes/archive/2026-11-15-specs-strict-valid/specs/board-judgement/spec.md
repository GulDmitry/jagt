## REMOVED Requirements

### Requirement: Orphaned worktrees are listed, each with its own delete verb
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

## ADDED Requirements

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
