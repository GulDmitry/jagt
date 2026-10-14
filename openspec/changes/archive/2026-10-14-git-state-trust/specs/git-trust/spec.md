## ADDED Requirements

### Requirement: A task records the git state it was cut with
A task SHALL carry one digest per config key and per hook file, read as its worktrees are cut (`GitTrust`). The
config is the repository's shared `config` and the worktree's `config.worktree`; the hooks are the directory
`core.hooksPath` names. It holds no value, folds to one digest per file past 512 entries, and leaves with the task.
The keys git writes as a branch is pushed are not recorded: `branch.<name>.remote`, `.merge`, `.rebase`,
`.pushremote`, `.description`.

#### Scenario: Cut
- **WHEN** `do ABC-42` cuts its worktree
- **THEN** the task holds the digests of what its git obeys

#### Scenario: Nothing recorded
- **WHEN** a task holding no record is checked
- **THEN** what the check reads becomes its record

### Requirement: Git with the human's credentials waits for trust
`ship`, `deploy` and `revert` (`GitStateGuard`) and a Master read (`ChargedReviews`) SHALL compare the record with
what git obeys now. A difference refuses, naming each key and file, and parks the task: `NEEDS YOU` on its card,
`trust` its only move among those three. A state that cannot be read refuses without parking.

#### Scenario: A session sets a program
- **WHEN** a session runs `git config core.fsmonitor /tmp/x` and you press Deploy
- **THEN** refused: `core.fsmonitor in <repo>/.git/config added`, nothing merged, the task parked

#### Scenario: The Master reads a changed task
- **WHEN** the Master's read meets a changed state
- **THEN** nothing is read and the round's question goes to you

#### Scenario: A push writes tracking
- **WHEN** a session pushes with `-u`
- **THEN** nothing parks

### Requirement: Only the human adopts a change
`trust` SHALL record what git obeys now as the task's record and unpark it. It is offered only to a parked task and
is the human's in every Master mode.

#### Scenario: Trust
- **WHEN** you press Trust git on a parked task
- **THEN** its record is what git obeys now, and `deploy` is offered again
