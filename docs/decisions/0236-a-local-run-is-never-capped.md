# 0236 — A local run is never capped in CPU or memory

[← decisions](README.md) · rule: [`ci`](../rules/ci.md) · amends [0233](0233-a-local-green-predicts-ci.md) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-24, by the owner's order.

## Context

- The local container ran with a CPU quota calibrated to a hosted runner's times, a runner's memory and a runner-sized concurrency.
- A hosted runner is only ever slower or faster than a laptop, so it is no reference for a quota.

## Decision

- `scripts/linux-suite.sh` runs its containers with no `--cpus`, `--memory` or `--memory-swap`.
- Gradle and the suites size their workers from the machine; the gate passes no `--max-workers`, `-PstandParallelism` or `-Pe2eForks`.
- A check passes on resources alone, never under a quota imitating a runner.
- `scripts/runner.env` is deleted; the `==> job` line names the job and its command only.

## Rejected

- A quota calibrated to the runner: it measures the calibration, not the code.

## Reopen when

- A check passes locally and fails on CI from resources alone, more than once, from one cause.
