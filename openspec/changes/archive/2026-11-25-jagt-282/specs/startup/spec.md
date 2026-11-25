## ADDED Requirements

### Requirement: The configuration is checked as written
The start check SHALL read `jagt.yml` as written and refuse what a read would drop or override in silence, every problem named with its key.
A reader that needs the file SHALL ask the configuration whether it exists, never the file system.
#### Scenario: A retired key
- **WHEN** `jagt.yml` holds a key nothing reads any more, such as `ui` or `openTerminalWindow`
- **THEN** startup refuses it naming `orchestrator.<key>`, why it went and the file to remove it from

#### Scenario: A config directory that shadows
- **WHEN** `./config/application.yml` exists in the working directory
- **THEN** startup refuses it: Spring reads it above `jagt.yml`, so it outranks every key it names

#### Scenario: An unknown view mode
- **WHEN** `viewer.viewMode` is neither `shared` nor `tab-per-task`
- **THEN** startup refuses `orchestrator.viewer.viewMode` and lists the two modes

#### Scenario: A viewer session the host cannot address
- **WHEN** `viewer.tmuxSession` names a session the terminal host cannot address
- **THEN** startup refuses `orchestrator.viewer.tmuxSession` with the host's reason

#### Scenario: A channel the driver finds wrong
- **WHEN** a channel's driver reports a fault in its settings, such as a `command` channel with an empty argv
- **THEN** startup refuses `orchestrator.notifications.channels.<name>.<setting>`
