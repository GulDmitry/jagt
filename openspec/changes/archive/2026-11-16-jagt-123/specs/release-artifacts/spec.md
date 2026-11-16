## ADDED Requirements

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
