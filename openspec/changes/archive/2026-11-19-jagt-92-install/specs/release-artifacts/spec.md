## ADDED Requirements

### Requirement: One line installs jagt per user
`install.sh` SHALL put a release jar, a Temurin 25 JRE and a `jagt` launcher under the user's own directories without sudo.
The root is `~/Library/Application Support/jagt` on macOS and `${XDG_DATA_HOME:-~/.local/share}/jagt` on Linux, `JAGT_HOME` overriding it.
The layout is `versions/<v>/jagt.jar`, `runtime/` and a `current` pointer; the launcher is `~/.local/bin/jagt`.
A new version installs beside the old; every network call has a timeout and fails on an HTTP error.
#### Scenario: A first install
- **WHEN** a user runs `install.sh` on macOS or Linux
- **THEN** `versions/<v>/jagt.jar`, `runtime/`, `current` and the `jagt` launcher exist, and `jagt` runs the current jar with the bundled runtime
#### Scenario: A newer release
- **WHEN** `install.sh` runs again for another version
- **THEN** both versions stay under `versions/` and `current` names the new one

### Requirement: A download is verified before it is kept
`install.sh` SHALL verify the jar against the release's `jagt-<version>.sha256`.
It SHALL verify the Java runtime against the sha256 Adoptium publishes beside it.
A mismatch discards the download and installs nothing; an installed jagt stays as it was.
#### Scenario: The jar fails its checksum
- **WHEN** the downloaded jar does not match its published sha256
- **THEN** the installer exits non-zero naming the mismatch, and no version, runtime or launcher is installed
#### Scenario: The runtime fails its checksum
- **WHEN** the downloaded Java runtime does not match its published sha256
- **THEN** the installer exits non-zero and nothing of it is installed
