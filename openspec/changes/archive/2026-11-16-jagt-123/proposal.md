## Why
A user could not tell that a release's jar or image was built by the release workflow from the tag, nor check a download against a checksum.

## What Changes
- The release job signs a SLSA build provenance attestation for the jar and for the image.
- The release carries a SHA-256 file for the jar and the SBOM, and one for the image's digest.
