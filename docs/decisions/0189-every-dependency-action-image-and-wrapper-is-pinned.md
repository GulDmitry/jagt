# 0189 — Every dependency, action, image and wrapper is pinned

[← decisions](README.md) · on [0186](0186-the-engineering-capability-is-the-one-inventory-of-gates.md),
[0188](0188-osv-scanner-on-the-release-sbom-is-the-vulnerability-scan.md)
Rule: [`engineering`](../../openspec/specs/engineering/spec.md)

## Status

Accepted 2026-11-16: a moved version, tag or `latest` changed a build with no diff (twelve-factor II, SLSA integrity).

## Context

- Dependencies resolved to whatever Maven Central held that day; actions and images were floating tags.
- The wrapper downloaded a distribution it never verified.
- Lockfiles pin versions, not the bytes behind them.

## Decision

- `jagt.conventions` locks every configuration of every project, `build-logic` too.
- The root and `build-logic` lock their plugin classpath.
- Lockfiles and checksums are written together by `./gradlew dependencies --write-locks --write-verification-metadata sha256`
  in `orchestrator-backend`, covering every project and included build, never by CI.
- The lock run resolves unlocked and fetches the metadata of every losing version, so it needs those checksums too.
- Default lock mode: a locked configuration fails on drift, an unlisted one resolves, so a new configuration never breaks a build.
- `distributionSha256Sum` holds the official checksum of the wrapper's version.
- Every action is a full commit sha with its tag as a trailing comment; every `FROM` and GitLab `image:` is `tag@sha256:digest`.
- A `FROM ${BASE}` is the user's own image and stays unpinned.
- Renovate (`config:recommended`, `helpers:pinGitHubActionDigests`) opens every move as a request a person reads.
- `gradle/verification-metadata.xml` holds the sha256 of every locked module and plugin, no trusted artifacts: a changed one fails the build.
- `PinningTest` fails on a missing lockfile, checksum, sha, digest or a locked module without an entry.

## Rejected

- Strict lock mode: every new configuration needs its lock written first; the drift check holds without it.
- Dependabot: no GitLab, no wrapper checksum, no Dockerfile digest in one config.
- Signature verification (`verify-signatures`): few publishers sign; checksums cover the integrity gap.

## Reopen when

- A configuration is found resolving unlocked and shipping.
- Renovate is unavailable on the host.
