# 0187 — A suite creates no more Spring contexts than its ceiling

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md) ·
rule: [`testing`](../rules/testing.md)

## Status

Accepted 2026-11-16, owner's order.

## Context

- A booted context is the slowest test; `boardTest` went from 16 context starts to 2 and from 129 s to 96 s by sharing one.
- The class-level ratchet (`contextBootingTests`) counts classes, not the contexts a run creates.
- Measured 2026-11-16: `test` 2, `boardTest` 2, `e2eTest` 3 to 4 (one cache per fork, classes spread by timing).

## Decision

- `test`, `boardTest` and `e2eTest` each end in a Gradle check summing Spring's own cache miss count, logged at DEBUG
  into the suite's test report, over its JVMs.
- The ceiling is the suite's `contexts*` entry in `complexity-ceilings.json`; past it the build fails naming the suite and count.
- Ceilings start at the measured maximum and only go down.

## Rejected

- Spring Test Profiler: no parallel execution (its README, checked 2026-11-16), and the unit group runs parallel.
- Reading the cache by reflection: its default cache is package-private.

## Reopens when

- A forked suite's count varies by more than its fork count, or Spring exposes the cache's statistics as an API.
