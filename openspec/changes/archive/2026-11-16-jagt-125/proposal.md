## Why
A release shipped a jar and an image with no list of what is inside them: a user could not check them against a vulnerability feed.

## What Changes
- The release job attaches a CycloneDX SBOM of the runtime dependencies beside the jar.
- The published image carries its SBOM as an OCI attestation.
