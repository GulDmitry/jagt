## MODIFIED Requirements

### Requirement: One line installs jagt per user
Launcher: `~/.local/bin/jagt`, on Windows `%LOCALAPPDATA%\jagt\bin\jagt.cmd`; it restarts the jar on exit 75.
#### Scenario: The launcher is stopped
- **WHEN** the `install.sh` launcher receives TERM or INT while its jar runs
- **THEN** the jar receives TERM and the launcher ends once the jar has ended
- **AND** the jar's port is free
