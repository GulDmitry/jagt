# 0226 — A stand timing is judged against its median

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · relates:
[0224](0224-a-compose-stands-data-folder-is-a-volume.md)

## Status

Accepted 2026-11-22.

## Context

- The CI stand job failed on `github-action-benchmark` with `alert-threshold: 200%`.
- The action compares a value with the previous single entry only.
- One fast run (5.96 s) made the next normal run (12.6 s) a "2.12x regression" on a shared runner.
- A nightly's startup took 35.3 s against a 16.8 s median while PR runs of the same commit passed.

## Decision

- `StandTimingGate` (Gradle task `standTimingGate`) reads the action's history file and the run's `timings.json`.
- A series fails when its value and its last history entry both pass 2x the median of its last 10 entries and its
  reference budget; it is named in the failure. A real slowdown fails one run later.
- A series with no history entry passes.
- The step runs before the action; the action keeps `fail-on-alert: false` and only stores the trend.

## Rejected

- A higher threshold: a real slowdown then passes, and the outlier still shifts the base.
- Averaging the history: one outlier still moves a mean.
- Retrying the stand job: hides the noise and doubles its cost.

## Reopen when

- The action compares with a median or a window itself.
- A runner stays noisy past the median and the 2x factor.
