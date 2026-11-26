# 0259 — Each pitest task stops at three times its measured run

[← decisions](README.md) · supersedes [0258](0258-pitest-tasks-run-without-a-gradle-time-limit.md) ·
on [0139](0139-every-suite-ends-within-one-ceiling.md) ·
spec: [`fitness`](../../openspec/specs/fitness/spec.md)

## Status

Accepted 2026-11-26.

## Context

- 0258 left the eight `pitest` tasks without a Gradle `timeout` until a run's time was measured.
- Measured 2026-11-26 from the nightly's per-project pitest jobs of runs 45285693099, 45309809543 and 45338595499 (2026-11-23/24),
  the longest job per project; those jobs left the nightly afterwards, so no newer run exists.

| project | longest run | run | hang guard (3x, rounded up) |
|---------|-------------|-----|-----------------------------|
| `:mutation-scope` | 7 min 28 s | 45309809543 | 23 min |
| `:statechart` | 10 min 6 s | 45338595499 | 31 min |
| `:surface` | 12 min 57 s | 45309809543 | 39 min |
| root | 14 min 43 s | 45309809543 | 45 min |
| `:adapter` | 24 min 22 s | 45338595499 | 74 min |
| `:core` | 25 min 35 s | 45309809543 | 77 min |
| `:stand` | at least 60 min 2 s, never finished (job limit) | 45309809543 | 181 min |
| `:usecase` | at least 150 min 16 s, never finished (cancelled) | 45285693099 | 451 min |

- Each job ran cold beside up to 20 others; the two lower bounds are not times of a finished run.

## Decision

- Every `pitest` task carries `timeout = Duration.ofMinutes(n)`, `n` from one map in `jagt.pitest.gradle`:
  three times its measured time (0139's hang guard).
- A project with a `pitest` task and no row in the map fails the build at configuration.
- `unlimited-runs` falls to 0.

## Reopens when

- A finished run of `:stand` or `:usecase` is measured: its limit is set from that time.
- A project's longest finished run changes by more than a third.
