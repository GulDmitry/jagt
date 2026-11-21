# 0217 — The post retry and the login lockout stay jagt's own

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) · relates: [0087](0087-the-industry-standard-library-before-our-own-code.md),
[0086](0086-the-breaker-is-resilience4js.md)

## Status

Accepted 2026-11-21. Reopens when a test below stops pinning its behaviour.

## Context

- 0087 asks for a decision where jagt still writes a job the industry has a library for.
- `RetryPolicy` is a value (attempts, wait, budget), not a mechanism; `PaidRead` already runs it through Spring's `RetryTemplate`.
- `ChannelPosts` retries on a scheduler, for ever, coalescing by key, warning once and marking the board on every failure.
- `LoginAttempts` is a size-capped map of lockouts that drops the least recently seen peer.

## Decision

- `RetryPolicy` stays; the library already runs it.
- `ChannelPosts` keeps its own schedule: its tests pin `schedule(…, 10|20|30, SECONDS)` on the injected executor and a
  newer message superseding an older one in flight; a retry library owns the loop and the unit of delay, so both pins change.
- Resilience4j is named in `adapter/` alone (0086); `usecase/` has no dependency on it.
- `LoginAttempts` keeps its map: `dropsTheLeastRecentlySeenPeerPastTheCap` pins least-recently-used eviction;
  Caffeine's default policy admits by frequency, so a peer that failed often outlives a newer one (estimate from its documentation, not run).
- Caffeine is not a direct dependency; it is on the annotation-processor path only.

## Rejected

- Resilience4j `Retry` in `usecase/`: breaks 0086's placement and the pinned unit of delay.
- Caffeine with `maximumSize`: changes the pinned eviction order.
