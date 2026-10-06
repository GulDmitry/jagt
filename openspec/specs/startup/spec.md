# startup Specification

## Purpose

What a start of jagt checks and refuses, where its configuration lives, and which file every agent CLI reads.

## Requirements

### Requirement: A bad install is refused at once, offline
A start SHALL refuse a missing or wrong setup with **every** problem at once, each naming the key that fixes it. It
checks nothing over the network; `--orchestrator.startup-checks=false` skips every check.

#### Scenario: Missing or wrong setup
- **WHEN** something the setup needs is missing or wrong, and you start jagt
- **THEN** it is refused with every problem at once, each naming the key that fixes it

#### Scenario: Wrong token or unreachable host
- **WHEN** a token is wrong or the host unreachable, and you start jagt
- **THEN** it is not detected: a start checks nothing over the network

#### Scenario: No desktop
- **WHEN** a suite boots on a machine with no desktop
- **AND** you pass `--orchestrator.startup-checks=false`
- **THEN** every check is skipped

#### Scenario: Linux with the platform unset
- **WHEN** you start jagt on Linux with the platform left unset
- **THEN** it is refused: unset means macOS

### Requirement: Configuration lives in jagt.yml
Every setting SHALL live in `jagt.yml` at the repository root, copied from `jagt.yml.dist`, where every key is
described. A command-line flag outranks the file; `projects` is re-read live, everything else needs a restart.

#### Scenario: Where a setting goes
- **WHEN** you ask where a setting goes
- **THEN** `jagt.yml` at the repository root, copied from `jagt.yml.dist`

#### Scenario: A key had no effect
- **WHEN** a key you set had no effect
- **THEN** a command-line flag outranks the file, or the key needs a restart: only `projects` is re-read live

#### Scenario: Legacy config.json
- **WHEN** jagt refuses to start over `config.json`
- **THEN** it is no longer read, and the refusal prints the `jagt.yml` to write instead

### Requirement: Every agent CLI reads the same rules
Whoever works **on** jagt, with any agent CLI, SHALL read the same `AGENTS.md` and reach the same MCP server.
See [components](../../../docs/rules/components.md#whoever-works-on-jagt-reads-the-same-file-and-reaches-the-same-server).

#### Scenario: Another agent CLI
- **WHEN** you open the repository with another agent CLI
- **THEN** it gets the same rules and the same MCP server
