# 0226 — A stand timing is judged against its median

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · relates:
[0224](0224-a-compose-stands-data-folder-is-a-volume.md)

## Status

Accepted 2026-11-22.

## Context

- The CI stand job failed on `github-action-benchmark` with `alert-threshold: 200%`.
- The action compares a value with the previous single entry only.
- One fast run (5.96 s) made the next normal run (12.6 s) a "2.12x regression" on a shared runner.

## Decision

- `StandTimingGate` (Gradle task `standTimingGate`) reads the action's history file and the run's `timings.json`.
- A series fails when its value passes 2x the median of its last 10 history entries; it is named in the failure.
- A series with no history entry passes.
- The step runs before the action; the action keeps `fail-on-alert: false` and only stores the trend.

## Rejected

- A higher threshold: a real slowdown then passes, and the outlier still shifts the base.
- Averaging the history: one outlier still moves a mean.
- Retrying the stand job: hides the noise and doubles its cost.

## Reopen when

- The action compares with a median or a window itself.
- A runner stays noisy past the median and the 2x factor.
