## MODIFIED Requirements

### Requirement: Configuration lives in jagt.yml
Every setting SHALL live in `jagt.yml` at the repository root, copied from `jagt.desktop.yml.dist`, where every key is
described. `jagt.server.yml.dist` is a short server starter that describes no key and points to the desktop file.
`ORCHESTRATOR_CONFIG_FILE` SHALL name another file to read instead, one per environment, with no profile involved.
A role's own options SHALL live in its folder, `roles/<id>/role.yml` over its `role.yml.dist`, which describes them.
A command-line flag outranks the file. `projects`, `viewer`, `agent`, `worktree`, `codeReview`,
`autoReview`, `master` and `tracker` SHALL be re-read on every access; `agent.cli`, `tracker.workflow` and every
other key need a restart.

#### Scenario: Where a setting goes
- **WHEN** you ask where a setting goes
- **THEN** `jagt.yml` at the repository root, copied from `jagt.desktop.yml.dist`; a role's environments, model and runner go in its folder

#### Scenario: A key had no effect
- **WHEN** a key you set had no effect
- **THEN** a command-line flag outranks the file, or the key is outside the live sections and needs a restart

#### Scenario: Changing the Master's mode
- **WHEN** you edit `roles.master.mode` while jagt runs
- **THEN** the next read of it uses the new mode, with no restart

#### Scenario: Legacy config.json
- **WHEN** jagt refuses to start over `config.json`
- **THEN** it is no longer read, and the refusal prints the `jagt.yml` to write instead

#### Scenario: The server starter
- **WHEN** `jagt.server.yml.dist` is copied to `jagt.yml`
- **THEN** it sets `onStop: end` and `logging: console`, and each key's description is in `jagt.desktop.yml.dist` alone

#### Scenario: One file per environment
- **WHEN** a team keeps `jagt.stage.yml` and `jagt.prod.yml` and sets `ORCHESTRATOR_CONFIG_FILE` to one
- **THEN** jagt reads that file alone
