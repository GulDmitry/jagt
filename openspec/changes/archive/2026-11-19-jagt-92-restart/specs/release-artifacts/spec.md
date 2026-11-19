## MODIFIED Requirements

### Requirement: One line installs jagt per user
`install.sh` (macOS, Linux) and `install.ps1` (Windows) SHALL put a release jar, a Temurin 25 JRE and a launcher per user, no sudo.
Roots: `~/Library/Application Support/jagt`, `${XDG_DATA_HOME:-~/.local/share}/jagt`, `%LOCALAPPDATA%\jagt`; `JAGT_HOME` overrides.
Layout: `versions/<v>/jagt.jar`, `runtime/`, `current`.
Launcher: `~/.local/bin/jagt`, on Windows `jagt.cmd` in `%LOCALAPPDATA%\jagt\bin`.
The launcher exports `JAGT_LAUNCHER` and runs the jar in a loop: while the jar exits 75 it reads `current` again and starts the jar;
any other exit code ends the launcher with that code.
Every network call has a timeout and fails on an HTTP error.
#### Scenario: A first install
- **WHEN** a user runs `install.sh` on macOS or Linux
- **THEN** `versions/<v>/jagt.jar`, `runtime/`, `current` and the `jagt` launcher exist, and `jagt` runs the current jar with the bundled runtime
#### Scenario: A first install on Windows
- **WHEN** a user runs `install.ps1` on Windows without admin rights
- **THEN** `versions/<v>/jagt.jar`, `runtime/`, a `current` file naming the version and `jagt.cmd` exist
- **AND** `jagt.cmd` runs the current jar with the bundled runtime, and its folder is on the user's PATH
#### Scenario: The jar asks to be run again
- **WHEN** the launcher's jar exits 75 after `current` was switched to another version
- **THEN** the launcher starts the jar of the version `current` now names, with the same arguments
#### Scenario: The jar ends otherwise
- **WHEN** the launcher's jar exits with any code but 75
- **THEN** the launcher ends with that code and starts nothing
#### Scenario: A newer release
- **WHEN** `install.sh` runs again for another version
- **THEN** both versions stay under `versions/` and `current` names the new one
#### Scenario: The install line is fetched
- **WHEN** a user downloads `releases/latest/download/install.sh`
- **THEN** they get the `install.sh` of the newest release
