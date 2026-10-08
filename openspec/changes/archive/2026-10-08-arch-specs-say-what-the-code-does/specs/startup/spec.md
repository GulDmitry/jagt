## MODIFIED Requirements

### Requirement: Configuration lives in jagt.yml
Every setting SHALL live in `jagt.yml` at the repository root, copied from `jagt.yml.dist`, where every key is
described. A command-line flag outranks the file. `projects`, `viewer`, `agent`, `worktree`, `codeReview`,
`autoReview`, `master` and `tracker` SHALL be re-read on every access; `agent.cli`, `tracker.workflow` and every
other key need a restart.

#### Scenario: Where a setting goes
- **WHEN** you ask where a setting goes
- **THEN** `jagt.yml` at the repository root, copied from `jagt.yml.dist`

#### Scenario: A key had no effect
- **WHEN** a key you set had no effect
- **THEN** a command-line flag outranks the file, or the key is outside the live sections and needs a restart

#### Scenario: Changing the Master's mode
- **WHEN** you edit `master.mode` while jagt runs
- **THEN** the next read of it uses the new mode, with no restart

#### Scenario: Legacy config.json
- **WHEN** jagt refuses to start over `config.json`
- **THEN** it is no longer read, and the refusal prints the `jagt.yml` to write instead

## ADDED Requirements

### Requirement: The steps the Master leaves you are checked at start
`master.mine` SHALL name only `task/MasterRight` steps, and `deploy` and `revert` both or neither; anything else
SHALL refuse the start (`startup/MasterCheck`).

#### Scenario: A misspelled step
- **WHEN** `master.mine` names a step `task/MasterRight` lacks
- **THEN** the start is refused, naming the steps there are

#### Scenario: Keeping deploy but not revert
- **WHEN** `master.mine` names `deploy` without `revert`
- **THEN** the start is refused: both write the same shared branch
