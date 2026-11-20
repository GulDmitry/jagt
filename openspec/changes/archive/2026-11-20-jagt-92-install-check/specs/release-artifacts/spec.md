## MODIFIED Requirements

### Requirement: The newest installer has a fixed URL
Every release SHALL attach `install.sh` and `install.ps1` under those names, so the latest release's download URLs serve the newest ones.
#### Scenario: A release is published
- **WHEN** the release job runs on a `v*` tag
- **THEN** the release holds `install.sh` and `install.ps1`

## ADDED Requirements

### Requirement: CI proves the installers end to end
CI SHALL run an install check on Linux, macOS and Windows runners before a release is published.
The check serves a fake feed on localhost and installs `0.0.1` with the platform's installer.
It starts the launcher with `orchestrator.releaseFeed.url` at that feed and runs `upgrade` once `0.0.2` is kept.
It fails naming the stage that failed, and every wait is bounded.
#### Scenario: Install, upgrade and restart
- **WHEN** the install check runs on a runner
- **THEN** `/state` answers under `0.0.1`, `versions/0.0.2/jagt.jar` is kept, `upgrade` makes `current` name `0.0.2`
- **AND** the launcher restarts jagt, `/state` answers again and `upgrade` is no longer offered

#### Scenario: A runtime checksum beside a redirected download
- **WHEN** the runtime feed redirects the download to a signed storage URL
- **THEN** the installer reads the runtime's checksum beside the feed's first redirect target

### Requirement: The launcher starts the jar by its version folder
The launcher SHALL start `versions/<v>/jagt.jar` for the version `current` names, never the path through `current`.
A running jagt then finds the releases kept beside it.
#### Scenario: A launcher on macOS or Linux starts the jar
- **WHEN** the launcher starts jagt while `current` names `1.2.3`
- **THEN** the jar on the command line is `<root>/versions/1.2.3/jagt.jar`
