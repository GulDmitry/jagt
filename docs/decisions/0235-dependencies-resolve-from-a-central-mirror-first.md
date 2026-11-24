# 0235 — Dependencies resolve from a Maven Central mirror first

[← decisions](README.md) · rule: [`ci`](../rules/ci.md) · on [0189](0189-every-dependency-action-image-and-wrapper-is-pinned.md),
[0233](0233-a-local-green-predicts-ci.md)

## Status

Accepted 2026-11-24: Maven Central answered 429 to hosted runners in PR runs 45322490446 and 45338587686 and in two
nightlies; Gradle's retries could not outlast it.

## Decision

- Every repository block lists Google's Maven Central mirror (`maven-central.storage-download.googleapis.com/maven2`)
  first, then the repository it mirrors: `mavenCentral()`, or `gradlePluginPortal()` for plugins.
- In `pluginManagement` the mirror skips plugin markers (`*.gradle.plugin`): they are the portal's, and the mirror's
  copies differ from the checksums `verification-metadata.xml` holds.
- Integrity stays with dependency verification (0189): the source of an artifact never decides whether it is trusted.

## Rejected

- More retries or longer back-off: the limit outlasted 75 s of back-off.
- A Gradle home baked into the CI image: it goes stale with every lockfile change and makes the image a cache.

## Reopen when

- The mirror lags Central for a locked version, or it answers errors itself.
