## MODIFIED Requirements

### Requirement: One line installs jagt per user
`install.sh` SHALL put a release jar, a Temurin 25 JRE and a `jagt` launcher under the user's own directories without sudo.
The root is `~/Library/Application Support/jagt` on macOS and `${XDG_DATA_HOME:-~/.local/share}/jagt` on Linux, `JAGT_HOME` overriding it.
The layout is `versions/<v>/jagt.jar`, `runtime/` and a `current` pointer; the launcher is `~/.local/bin/jagt`.
A new version installs beside the old; every network call has a timeout and fails on an HTTP error.
Every release attaches `install.sh` under that name, so the latest release's download URL serves the newest one.
#### Scenario: A first install
- **WHEN** a user runs `install.sh` on macOS or Linux
- **THEN** `versions/<v>/jagt.jar`, `runtime/`, `current` and the `jagt` launcher exist, and `jagt` runs the current jar with the bundled runtime
#### Scenario: A newer release
- **WHEN** `install.sh` runs again for another version
- **THEN** both versions stay under `versions/` and `current` names the new one
#### Scenario: The install line is fetched
- **WHEN** a user downloads `releases/latest/download/install.sh`
- **THEN** they get the `install.sh` of the newest release
