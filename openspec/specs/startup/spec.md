# startup Specification

## Purpose

What a start of jagt checks and refuses, where its configuration lives, and which file every agent CLI reads.

## Requirements

### Requirement: A bad install is refused at once, offline
A start SHALL refuse a missing or wrong setup with **every** problem at once, each naming the key that fixes it; a
tool only tidying up after a task (`lsof`, `pkill`) missing is said once and starts anyway. It checks nothing over
the network; `--orchestrator.startup-checks=false` skips the checks about the machine, never those of jagt's own files.

#### Scenario: Missing or wrong setup
- **WHEN** something the setup needs is missing or wrong, such as an unknown `agent.cli` or a moved `agentDisabledPlugins`, and you start jagt
- **THEN** it is refused with every problem at once, each naming its key: an unknown `agent.cli` its value, a moved one its new place

#### Scenario: Wrong token or unreachable host
- **WHEN** a token is wrong or the host unreachable, and you start jagt
- **THEN** it is not detected: a start checks nothing over the network

#### Scenario: No desktop
- **WHEN** a suite boots on a machine with no desktop
- **AND** you pass `--orchestrator.startup-checks=false`
- **THEN** every check of the machine is skipped

#### Scenario: Checks off, cases missing
- **WHEN** you start jagt with the checks off and no case file in its root
- **THEN** it is refused, naming the steps no case sends

#### Scenario: The board port is held
- **WHEN** another jagt or process listens on the board port, and you start jagt
- **THEN** it exits at once with one line naming the port, and logs no ERROR and no stack

#### Scenario: Linux with the platform unset
- **WHEN** you start jagt on Linux with the platform left unset
- **THEN** it starts with the Linux drivers

#### Scenario: A platform other than the machine's
- **WHEN** `platform` names an OS other than the one the machine reports, and you start jagt
- **THEN** it is refused, naming both

### Requirement: Configuration lives in jagt.yml
Every setting SHALL live in `jagt.yml` at the repository root, copied from `jagt.desktop.yml.dist`, where every key is
described. `jagt.server.yml.dist` is a short server starter that describes no key and points to the desktop file.
`ORCHESTRATOR_CONFIG_FILE` SHALL name another file to read instead, one per environment, with no profile involved.
A role's own options SHALL live in its folder, `roles/<id>/role.yml` over its `role.yml.dist`, which describes them.

#### Scenario: Where a setting goes
- **WHEN** you ask where a setting goes
- **THEN** `jagt.yml` at the repository root, copied from `jagt.desktop.yml.dist`; a role's environments, model and runner go in its folder

#### Scenario: Legacy config.json
- **WHEN** jagt refuses to start over `config.json`
- **THEN** it is no longer read, and the refusal prints the `jagt.yml` to write instead

#### Scenario: The server starter
- **WHEN** `jagt.server.yml.dist` is copied to `jagt.yml`
- **THEN** it sets `onStop: end` and `logging: console`, and each key's description is in `jagt.desktop.yml.dist` alone

#### Scenario: One file per environment
- **WHEN** a team keeps `jagt.stage.yml` and `jagt.prod.yml` and sets `ORCHESTRATOR_CONFIG_FILE` to one
- **THEN** jagt reads that file alone

### Requirement: Some keys are re-read live
A command-line flag outranks the file. `projects`, `viewer`, `agent`, `worktree`, `codeReview`,
`autoReview`, `master` and `tracker` SHALL be re-read on every access; `agent.cli`, `tracker.workflow` and every
other key need a restart.

#### Scenario: A key had no effect
- **WHEN** a key you set had no effect
- **THEN** a command-line flag outranks the file, or the key is outside the live sections and needs a restart

#### Scenario: Changing the Master's mode
- **WHEN** you edit `roles.master.mode` while jagt runs
- **THEN** the next read of it uses the new mode, with no restart

### Requirement: Every agent CLI reads the same rules
Whoever works **on** jagt, with any agent CLI, SHALL read the same `AGENTS.md` and reach the same MCP server.
See [components](../../../docs/rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server).

#### Scenario: Another agent CLI
- **WHEN** you open the repository with another agent CLI
- **THEN** it gets the same rules and the same MCP server

### Requirement: The steps the Master leaves you are checked at start
`roles.master.mine` SHALL name only `task/master/MasterRight` steps, `TaskAction` ids or `GlobalCommand` ids, and in `act` `deploy` and
`revert` both or
neither; anything else SHALL refuse the start (`startup/role/MasterCheck`).

#### Scenario: A misspelled step
- **WHEN** `roles.master.mine` names a word that is no step and no verb
- **THEN** the start is refused, naming the words there are

#### Scenario: Keeping deploy but not revert
- **WHEN** `roles.master.mine` names `deploy` without `revert` in `roles.master.mode: act`
- **THEN** the start is refused: both write the same shared branch

### Requirement: The Master is configured under its role
The Master SHALL be configured under `orchestrator.roles.master`, with `mode`, `model`, `brief` and `mine`. The old
`orchestrator.master` key SHALL still be read; `roles.master` wins field by field and the old key fills each field
it leaves unwritten. Reading the old key logs one
deprecation line and is never a startup problem.

#### Scenario: Only the old key
- **WHEN** `jagt.yml` writes `master: {mode: act}` and no `roles.master`
- **THEN** the Master acts, and one deprecation line is logged

#### Scenario: Both keys
- **WHEN** `jagt.yml` writes `master: {mode: act}` and `roles: {master: {mode: judge}}`
- **THEN** the Master judges

#### Scenario: A partial role key
- **WHEN** `jagt.yml` writes `master: {mine: [plan, deploy, revert]}` and `roles: {master: {mode: act}}`
- **THEN** the Master acts and `mine` stays `[plan, deploy, revert]`

### Requirement: A role folder that cannot run a check is the board's
An enabled acceptance whose folder cannot run a check SHALL be one mark on the board's head, and no card stops.
It cannot with no environment, a flows driver jagt lacks, or a driver with no path or command.
The mark SHALL end on the first read finding the folder fixed.

#### Scenario: Enabled with no environment
- **WHEN** `roles.acceptance.enabled` is true and the folder names no environment
- **THEN** the board's head names `roles/acceptance/role.yml`, and adding an environment ends the mark with no restart

### Requirement: The Master's token is written beside state.json
jagt SHALL write the Master's token to `master-token` beside `state.json`, mode 0600, never into the root.
A read-only root (an installed jar, a mounted config) still starts. A session's shell line naming that file SHALL be
refused (`ToolGate`).

#### Scenario: A read-only root
- **WHEN** the root is read-only and the state folder writable
- **THEN** jagt starts and the Master reaches `/mcp` with its token

#### Scenario: A session names the token
- **WHEN** a session's shell line names the token's path
- **THEN** `ToolGate` refuses it

### Requirement: A value may come from the environment or a file
A string in `jagt.yml` SHALL take `${VAR}`, `${VAR:default}` and `${file:/path}`, then `JAGT__a__b=value` SHALL set the key `a.b`.
A change in a variable or file a read used SHALL be picked up by the next one. A resolved value SHALL never be logged.

#### Scenario: A variable fills a value
- **WHEN** a value is `${TOKEN}` and `TOKEN` is set
- **THEN** the key reads its value, and no log line carries it

#### Scenario: A file fills a value
- **WHEN** a value is `${file:/run/secrets/x}`
- **THEN** the key reads that file's content, trailing newline cut

#### Scenario: A variable overrides a key
- **WHEN** `JAGT__server__publicUrl` is set
- **THEN** it wins over `jagt.yml`'s `server.publicUrl`

#### Scenario: A variable that is not set
- **WHEN** a placeholder names an unset variable with no default
- **THEN** the start is refused naming the key and the variable

#### Scenario: Nothing to expand
- **WHEN** `jagt.yml` holds no placeholder and no `JAGT__` variable is set
- **THEN** it loads as before

### Requirement: One data folder holds everything jagt writes
jagt SHALL write `jagt/` (state and marks), `projects/`, `tasks/<task>/` (its worktrees, deploy and revert ones included),
`archive/`, `records/`, `memory/` and `logs/` under one data folder.
The folder is `orchestrator.data`, default `data/` under the root, overridden by `JAGT__orchestrator__data` or `ORCHESTRATOR_DATA`.
`jagt.yml` stays outside it. A project's `path` is used in place; a task in `state.json` keeps its recorded worktree.
#### Scenario: No data key
- **WHEN** jagt starts with no `orchestrator.data`
- **THEN** state, records, tasks, clones and logs are written under `data/` in the root and nowhere else
#### Scenario: A data key
- **WHEN** `orchestrator.data` or `JAGT__orchestrator__data` names a folder
- **THEN** everything is written there
#### Scenario: A repository elsewhere
- **WHEN** a project's path lies outside the data folder
- **THEN** it is used in place and a new task's worktree is cut under `tasks/<task>/worktrees/`
#### Scenario: A repository in a read-only folder
- **WHEN** a project's path lies in a folder jagt cannot write in
- **THEN** jagt starts, and a deploy or revert cuts its worktree under `tasks/<task>/worktrees/`
#### Scenario: A state from an older version
- **WHEN** the root, `~/.jagt` or the data folder itself holds the `state.json` of an older version
- **THEN** that folder alone moves, logged per file; a kept name is warned, its master token deleted, a moved clone's worktrees repaired
#### Scenario: A data folder that cannot be written
- **WHEN** the data folder is a file or read-only
- **THEN** the start is refused naming `orchestrator.data`

### Requirement: Logging is a seam, file or console
`orchestrator.logging` SHALL select where jagt logs: `file` (default) writes the backend log and the Master's feed under `logs/` as before;
`console` writes no log file.
It writes every line, the Master's included, as one ECS JSON object on stdout: the event is the message, each field its own member.
Any other value SHALL be refused at start naming the key.
#### Scenario: Console logging
- **WHEN** `logging` is `console` and jagt logs a line with fields
- **THEN** stdout carries one JSON object for it with the event and every field, and no file is created under `logs/`
#### Scenario: Logging unset
- **WHEN** `logging` is unset or `file`
- **THEN** the log files are written under `logs/` as before
#### Scenario: An unknown value
- **WHEN** `logging` is `syslog`
- **THEN** the start is refused naming `orchestrator.logging`
