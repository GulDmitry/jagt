# release-artifacts Specification

## Purpose
What a release publishes beside the jar and the image, so a user can verify what they run. The line that installs it.

## Requirements

### Requirement: A release lists what is inside it
The release job SHALL generate a CycloneDX SBOM of the runtime classpath and attach it to the release as `jagt-<version>.cdx.json`
beside the jar. The published image SHALL carry its own SBOM as an attestation of its index.
#### Scenario: A tag is released
- **WHEN** the release job runs on a `v*` tag
- **THEN** the release holds `jagt-<version>.jar` and `jagt-<version>.cdx.json`
#### Scenario: The image is published
- **WHEN** the release job publishes the image
- **THEN** its index carries an SBOM attestation for each platform

### Requirement: A release proves where it was built
The release job SHALL sign a build provenance attestation for the jar and for the image's index.
`gh attestation verify` SHALL verify it.
The release SHALL carry `jagt-<version>.sha256` for the jar and the SBOM, and `jagt-<version>.image.sha256` for the image's digest.
#### Scenario: The jar is verified
- **WHEN** a user runs `gh attestation verify jagt-<version>.jar` on a released jar
- **THEN** it names the release workflow and the tag the jar was built from
#### Scenario: The image is verified
- **WHEN** a user runs `gh attestation verify oci://<image>:<version>` on a released image
- **THEN** it names the release workflow and the tag the image was built from
#### Scenario: A download is checked
- **WHEN** a user runs `sha256sum -c jagt-<version>.sha256` beside the downloaded jar and SBOM
- **THEN** both match

### Requirement: One line installs jagt per user
`install.sh` (macOS, Linux) and `install.ps1` (Windows) SHALL put a release jar, a Temurin 25 JRE and a launcher per user, no sudo.
Roots: `~/Library/Application Support/jagt`, `${XDG_DATA_HOME:-~/.local/share}/jagt`, `%LOCALAPPDATA%\jagt`; `JAGT_HOME` overrides.
Layout: `versions/<v>/jagt.jar`, `runtime/`, `current`.
Launcher: `~/.local/bin/jagt`, on Windows `%LOCALAPPDATA%\jagt\bin\jagt.cmd`; it restarts the jar on exit 75.
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
- **AND** every jar it starts sees `JAGT_LAUNCHER` set
#### Scenario: The jar ends otherwise
- **WHEN** the launcher's jar exits with any code but 75
- **THEN** the launcher ends with that code and starts nothing
#### Scenario: A newer release
- **WHEN** `install.sh` runs again for another version
- **THEN** both versions stay under `versions/` and `current` names the new one
#### Scenario: The install line is fetched
- **WHEN** a user downloads `releases/latest/download/install.sh`
- **THEN** they get the `install.sh` of the newest release

### Requirement: A download is verified before it is kept
`install.sh` and `install.ps1` SHALL verify the jar against the release's `jagt-<version>.sha256`.
They SHALL verify the Java runtime against the sha256 Adoptium publishes beside it.
A mismatch discards the download and installs nothing; an installed jagt stays as it was.
#### Scenario: The jar fails its checksum
- **WHEN** the downloaded jar does not match its published sha256
- **THEN** the installer exits non-zero naming the mismatch, and no version, runtime or launcher is installed
#### Scenario: The runtime fails its checksum
- **WHEN** the downloaded Java runtime does not match its published sha256
- **THEN** the installer exits non-zero and nothing of it is installed

### Requirement: The newest installer has a fixed URL
Every release SHALL attach `install.sh` under that name, so the latest release's download URL serves the newest one.
#### Scenario: A release is published
- **WHEN** the release job runs on a `v*` tag
- **THEN** the release holds `install.sh`

### Requirement: A running jagt keeps a newer release verified beside it
Run from `<root>/versions/<v>/`, jagt SHALL hourly keep the feed's newer release under `versions/` once its jar matches its sha256.
The feed is `orchestrator.releaseFeed.kind` (`github` default, `none`) at `orchestrator.releaseFeed.url`.
`current` is never changed, and every network call has a timeout.
#### Scenario: A newer release is kept verified beside the running one
- **WHEN** the feed names a newer version and its jar matches the published sha256
- **THEN** `versions/<version>/jagt.jar` exists beside the running version and `current` is unchanged
#### Scenario: A mismatch is discarded
- **WHEN** the downloaded jar does not match its published sha256
- **THEN** nothing is kept under `versions/` and no staging folder remains
#### Scenario: Not an installed layout
- **WHEN** jagt runs from a jar outside `<root>/versions/<v>/`
- **THEN** the feed is not asked and nothing is downloaded

### Requirement: An upgrade switches current to the kept release
A kept release SHALL carry the sha256 it was verified with as `versions/<v>/jagt.jar.sha256`.
An upgrade SHALL verify `versions/<v>/jagt.jar` against it again, then point `current` at `<v>`:
a symlink replaced atomically on macOS and Linux, the version file rewritten on Windows.
The previous version stays under `versions/`.
A mismatch, or a release kept without a checksum, leaves `current` unchanged and is refused with its reason.
#### Scenario: A kept release is verified and switched
- **WHEN** an upgrade to a kept `0.25.0` finds its jar matching the stored sha256
- **THEN** `current` names `0.25.0` and the old version stays under `versions/`
#### Scenario: The kept jar no longer matches
- **WHEN** an upgrade finds the kept jar differing from the stored sha256
- **THEN** `current` is unchanged and the failure names the mismatch
