## ADDED Requirements

### Requirement: A release lists what is inside it
The release job SHALL generate a CycloneDX SBOM of the runtime classpath and attach it to the release as `jagt-<version>.cdx.json`
beside the jar. The published image SHALL carry its own SBOM as an attestation of its index.
#### Scenario: A tag is released
- **WHEN** the release job runs on a `v*` tag
- **THEN** the release holds `jagt-<version>.jar` and `jagt-<version>.cdx.json`
#### Scenario: The image is published
- **WHEN** the release job publishes the image
- **THEN** its index carries an SBOM attestation for each platform
