# 0199 — The image layers a prebuilt jar on published base images

[← decisions](README.md) · on [0188](0188-osv-scanner-on-the-release-sbom-is-the-vulnerability-scan.md),
[0189](0189-every-dependency-action-image-and-wrapper-is-pinned.md) · amends [0171](0171-the-image-is-a-base-without-agent-clis.md)

## Status

Accepted 2026-11-18 by the owner's order (JAGT-127): a base image, published and pinned by digest, the app on top.

## Context

- CI's stand job spends about 150 s of 396 s building the image, 136 s of it `./gradlew bootJar` inside Docker
  whose cache mount a fresh runner never has (CI runs 2026-11-08..18).
- The stand layer (nodejs, the agent CLI) and the OS packages rebuild on every run though they change rarely.

## Decision

- The app `Dockerfile` reads the jar from a stage named `jar`; `--build-context jar=<dir>` overrides it with the staged
  `bootJar`, and without the flag that stage builds the jar, so `docker build .` keeps working.
- Compose passes the jar as `build.additional_contexts`; `standImage`, `container-suite.sh` and `publish-image.sh` do the same.
- The OS layer (git, tmux, tini, procps, lsof, openssh-client, uid 10001, gid-0 permissions) and the stand layer are
  Dockerfiles of their own, published to ghcr.io and named by digest, never a floating tag.
- Base images are built amd64 and arm64 with signed provenance, an SBOM and the osv-scanner scan the app image carries.
- The OS layer is `base/Dockerfile`, built by `.github/workflows/base-images.yml` (push to `base/**`, weekly, manual).
- The stand layer is `base/Dockerfile.stand`, published as `<repository>-stand-base` after the OS base, `FROM` it by tag.
- They rebuild in their own workflow on a change to a base Dockerfile and weekly for OS security fixes.
- Renovate bumps the temurin digest in a base Dockerfile and the base digest in an app Dockerfile, as a request passing full CI.
- After each publish the workflow keeps the newest 10 untagged base images with their per-arch and attestation children
  and deletes partial and orphaned ones (`dataaxiom/ghcr-cleanup-action`, multi-arch aware); a release's pinned base
  stays pullable for about 10 weeks, and the release package is never pruned by it.
- CI caches Gradle with `gradle/actions/setup-gradle`, `cache-provider: basic`, written only from `main`.

## Rejected

- Jib and `bootBuildImage`: no `RUN` step, so no OS packages and no 0171 Dockerfile contract.
- buildx `type=gha` layer cache: experimental, and a builder only CI has.

## Reopens when

- ghcr.io stops being the registry the release publishes to.
- A base image needs a package per user, not per release.
