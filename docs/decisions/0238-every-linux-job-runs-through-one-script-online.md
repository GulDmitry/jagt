# 0238 — Every Linux job runs through one script, online; a cache only speeds it up

[← decisions](README.md) · rule: [`ci`](../rules/ci.md) · amends [0233](0233-a-local-green-predicts-ci.md) ·
on [0235](0235-dependencies-resolve-from-a-central-mirror-first.md), [0162](0162-a-check-holds-for-all-code.md),
[0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-24, by the owner's order: local equals CI, so every release is stable.

## Context

- Nightly run 45407359631 went red on a cache the local twin never exercised: Spotless could not resolve
  `google-java-format` and the board audit hit `ENOTCACHED`.
- The twin ran `--network none` after a warm-up of its own choosing, so its cache decided whether a run passed; a runner has its network.
- `test` and the kind `stand` ran in other shapes (a `container:` job as root, a host kind), so no one script was CI's twin.

## Decision

- Every Linux job runs `scripts/linux-suite.sh <job>`, in the pinned CI image, with its network, locally and on CI.
  `test` and the kind stand (`stand-kind`) join it: no `container:` job, no root special case.
- The Gradle home and npm cache are a plain speed cache: no warm-up, no read-only seed, no offline flag. An empty one is slower, never red.
- Maven Central's rate limit is the mirror's ([0235](0235-dependencies-resolve-from-a-central-mirror-first.md)), not a cache's.
- A CI cache key is exact: the job plus a hash of the build files, no `restore-keys` prefix fallback.
- The runner's engine starts as moby's `hack/dind` does, its cgroup delegation retried until it holds.
- The CI image's digest is the workflow's `JAGT_CI_IMAGE`, which the script reads.
- Amends 0233: T1 is online; the rest of 0233 stands.

## Rejected

- Docker's host socket: the job would share and leave images on the host engine.
- Host jobs: they run on whatever the machine has.
- Dagger: a second pipeline language beside the workflow.
- A local VM: it needs its own image and keeps a second OS to maintain.
- Offline runs on a warmed cache: the cache then decides the verdict, and the warm-up is a second gate to keep equal to the first.

## Reopen when

- Kind in Docker-in-Docker goes red on CI twice from the nesting itself, not from the code under test.
