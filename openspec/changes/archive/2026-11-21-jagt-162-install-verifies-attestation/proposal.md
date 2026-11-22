## Why

The install checked a sha256 published beside the jar, so a tampered release passed; the attestation proves where the bytes were built.

## What Changes

- The release attests the jar, the SBOM and the `.sha256` file.
- `install.sh` and `install.ps1` verify the jar's and the checksum file's attestation when `gh` is on PATH and refuse on failure.

## Capabilities

### New Capabilities

### Modified Capabilities

- `release-artifacts`: the attested subjects and the install's verification.
