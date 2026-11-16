# 0182 — A release lists what is inside it, as CycloneDX

[← decisions](README.md) · rule: [`release-artifacts`](../../openspec/specs/release-artifacts/spec.md)

## Status

Accepted 2026-11-16: the baseline audit found no SBOM for the jar or the image.

## Context

- A user running jagt's jar or image cannot match its libraries against a vulnerability feed without a list of them.
- The jar bundles every module's runtime classpath; the image adds an OS layer and a JRE.

## Decision

- **The CycloneDX Gradle plugin** (`org.cyclonedx.bom` 3.6.1, latest on 2026-11-16) in its own convention, `jagt.sbom`:
  `cyclonedxBom` writes `build/reports/sbom/jagt.cdx.json`, CycloneDX 1.6 JSON.
- **Attached to the release** as `jagt-<version>.cdx.json` beside the jar; the GitLab release job keeps it as an artifact.
- **The image's SBOM is BuildKit's**: `docker buildx build --sbom=true` scans the built image, OS packages included, and
  stores it in the index; no second tool, no file to host.

- **Ceilings raised by one** ([0176](0176-every-kind-has-a-ceiling.md)): `buildLogic` 15 (the `jagt.sbom` convention),
  `dependencyLinesInBuildGradle` 55 (its plugin), `specCapabilities` 45 (`release-artifacts`).

## Rejected

- Syft or Trivy on the jar: a second tool for what the build tool already knows.
- SPDX: the Gradle plugin and the buildx scanner both write CycloneDX; one format for both.

## Reopen when

- A consumer needs SPDX, or the plugin's output omits a dependency the jar bundles.

Sources checked 2026-11-16: CycloneDX Gradle plugin README and Gradle Plugin Portal metadata (3.6.1); Docker docs "SBOM attestations"
(`--sbom`); CycloneDX specification 1.6.
