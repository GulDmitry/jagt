## MODIFIED Requirements

### Requirement: A bad install is refused at once, offline
A start SHALL refuse a missing or wrong setup with **every** problem at once, each naming the key that fixes it. It
checks nothing over the network; `--orchestrator.startup-checks=false` skips the checks about the machine, never
those of jagt's own files.

#### Scenario: Missing or wrong setup
- **WHEN** something the setup needs is missing or wrong, and you start jagt
- **THEN** it is refused with every problem at once, each naming the key that fixes it

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

#### Scenario: Linux with the platform unset
- **WHEN** you start jagt on Linux with the platform left unset
- **THEN** it is refused: unset means macOS
