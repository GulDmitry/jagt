# 0202 — A running jagt reads the newest release through its own port

[← decisions](README.md) · on [0130](0130-every-third-party-is-a-configured-seam.md),
[0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-19 (JAGT-92, JAGT-134).

## Context

- jagt keeps the newest release beside the one it runs, so an upgrade needs no download at the moment it is asked.
- Where releases are published is a third-party service: a seam chosen in `jagt.yml`, never a URL in code.

## Decision

- `ReleaseFeed` is a port: `GithubReleaseFeed` reads a host's releases, `NoReleaseFeed` reads none.
- `ReleaseCheckJob` is a `Job` reading the feed; `releaseFeed.kind` in `jagt.yml` picks the driver.
- **Ceilings raised by one** ([0176](0176-every-kind-has-a-ceiling.md)): `ports` 52 (`ReleaseFeed`),
  `startTriggers` 57 (`ReleaseCheckJob`).

## Rejected

- The feed folded into the code-host port: a release feed is read where no code host is configured.

## Reopens when

- A second reader of releases joins and the two can share one port.
