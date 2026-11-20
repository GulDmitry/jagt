# 0207 — A kept release is switched on its own only when `releaseFeed.autoUpgrade` is on

[← decisions](README.md) · on [0204](0204-exit-code-75-means-run-me-again.md),
[0201](0201-an-approved-request-deploys-itself-when-deploy-on-approval-is-on.md)

## Status

Accepted 2026-11-20, owner's choice (JAGT-92).

## Context

- An install on a server has nobody at the board to press `upgrade`; a kept release waits for ever.
- A switch ends jagt with exit 75, and every agent session with it.
- AGENTS.md allowed no unasked act but the Master's and those a tracker or code host reports.

## Decision

- A new unasked actor: the release feed's newest verified release, read by `ReleaseCheckJob`.
- `orchestrator.releaseFeed.autoUpgrade` in `jagt.yml` turns it on; the default is `false`, in both starters.
- It runs the one switch the `upgrade` verb runs (`ReleaseSwitch`), after each release check.
- It switches only under the launcher: without it nothing starts jagt again.
- It waits while any task has an agent session up (working, idle or quiet) and tries again at the next check.
- A refused switch (checksum mismatch, no checksum) is tried once per version: the release stays kept, the board's `upgrade` names the refusal,
  another kept version is tried.

## Rejected

- On by default: a restart is the one thing the owner of an install never asked for.
- Ending the sessions to make room: a running agent's turn is lost work.
- Retrying a refused switch each check: a tampered jar would log a refusal every hour.

## Reopens when

- A session survives a restart, so waiting for none stops being needed.
