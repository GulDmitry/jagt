## ADDED Requirements

### Requirement: The install verifies the attestation
With `gh` on PATH, `install.sh` and `install.ps1` SHALL run `gh attestation verify` on the downloaded jar and checksum file.
They SHALL install nothing on a failure. Without `gh` they SHALL say once that provenance was not verified and keep the sha256 check.

#### Scenario: An attestation fails
- **WHEN** `gh attestation verify` exits non-zero for a downloaded release asset
- **THEN** the install ends non-zero and installs nothing

#### Scenario: gh is missing
- **WHEN** `gh` is not on PATH
- **THEN** the install checks the sha256 and says provenance was not verified

## MODIFIED Requirements

### Requirement: A release proves where it was built
The release job SHALL sign a build provenance attestation for the jar, the SBOM, the `.sha256` file and the image's index.
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
