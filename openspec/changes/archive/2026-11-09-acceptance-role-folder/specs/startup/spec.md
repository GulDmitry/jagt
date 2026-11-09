## MODIFIED Requirements

### Requirement: Configuration lives in jagt.yml
Every setting SHALL live in `jagt.yml` at the repository root, copied from `jagt.yml.dist`, where every key is
described. A role's own options SHALL live in its folder, `roles/<id>/role.yml` over its `role.yml.dist`, which describes them.
A command-line flag outranks the file. `projects`, `viewer`, `agent`, `worktree`, `codeReview`,
`autoReview`, `master` and `tracker` SHALL be re-read on every access; `agent.cli`, `tracker.workflow` and every
other key need a restart.

#### Scenario: Where a setting goes
- **WHEN** you ask where a setting goes
- **THEN** `jagt.yml` at the repository root, copied from `jagt.yml.dist`; a role's environments, model and runner go in its folder

#### Scenario: A key had no effect
- **WHEN** a key you set had no effect
- **THEN** a command-line flag outranks the file, or the key is outside the live sections and needs a restart

#### Scenario: Changing the Master's mode
- **WHEN** you edit `roles.master.mode` while jagt runs
- **THEN** the next read of it uses the new mode, with no restart

#### Scenario: Legacy config.json
- **WHEN** jagt refuses to start over `config.json`
- **THEN** it is no longer read, and the refusal prints the `jagt.yml` to write instead

## ADDED Requirements

### Requirement: A role folder that cannot run a check is the board's
An enabled acceptance whose folder cannot run a check SHALL be one mark on the board's head, and no card stops.
It cannot with no environment, a flows driver jagt lacks, or a driver with no path or command.
The mark SHALL end on the first read finding the folder fixed.

#### Scenario: Enabled with no environment
- **WHEN** `roles.acceptance.enabled` is true and the folder names no environment
- **THEN** the board's head names `roles/acceptance/role.yml`, and adding an environment ends the mark with no restart
