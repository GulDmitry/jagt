# 0183 — A release is signed by the platform that built it

[← decisions](README.md) ·
rule: [`release-artifacts`](../../openspec/specs/release-artifacts/spec.md) · on [0182](0182-a-release-lists-what-is-inside-it-as-cyclonedx.md)

## Status

Accepted 2026-11-16: the baseline audit found no provenance and no checksum for the jar or the image (SLSA build level 2).

## Context

- SLSA build L2 asks a hosted build platform to generate and sign the provenance, which a consumer verifies.
- The release job already builds both on a GitHub-hosted runner from the tag.

## Decision

- **`actions/attest-build-provenance`** (v4.2.2, checked 2026-11-16), pinned to its commit SHA with the version in a comment,
  signs with the workflow's OIDC identity through Sigstore: no key of ours, none to rotate.
  The job gains `attestations: write` and nothing else new.
- **The jar** is the subject by path; **the image** by name and the index digest `docker buildx --metadata-file` reports, pushed to
  the registry beside the image. BuildKit's own `--provenance=mode=max` stays: it records the build inputs inside the index.
- **SHA-256 files beside the assets**: `jagt-<version>.sha256` over the jar and the SBOM (`sha256sum -c` checks it),
  `jagt-<version>.image.sha256` with the index digest.
- **Verified with** `gh attestation verify`.
- GitLab's release job signs nothing: the GitHub release is the one published, and GitLab's was not examined.

## Rejected

- `slsa-github-generator`: a reusable workflow at L3 that must be referenced by tag, not SHA; L2 is the target.
- Cosign with a key: a key to hold and rotate; keyless needs no secret.

## Reopen when

- jagt targets SLSA build L3, or ships through a registry that GitHub attestations do not reach.

Sources checked 2026-11-16: GitHub Docs "Using artifact attestations to establish provenance for builds"; the action's README
(v4); slsa.dev "SLSA specification v1.0, build track" (L2); Docker docs "Provenance attestations".
