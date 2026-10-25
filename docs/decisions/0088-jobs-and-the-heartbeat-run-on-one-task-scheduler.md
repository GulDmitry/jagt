# 0088 — Jobs and the heartbeat run on one TaskScheduler

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-25.

## Context

- `Jobs` polled every job each second and handed due ones to a worker pool; the board's heartbeat had its own executor.
- Spring's `TaskScheduler.schedule(Runnable, Trigger)` does both, and its rescheduling runnable never overlaps a chain.

## Decision

- One `TaskScheduler` bean, `jobs + 1` threads, on jagt's `Clock`; a job runs on its scheduler thread.
- Each job's `Trigger` returns now the first time, then the last run's start plus `job.every()`; none for a `null` interval.
- An interval a job cannot name is retried one second after the start, booked as a failure.
- `run <job>` cancels the job's pending run and schedules a fresh chain; `running` stays a guard against any double start.
- The heartbeat is `scheduleAtFixedRate` on the same scheduler, cancelled in the context-closed listener after the browsers end.

## Rejected

- `fixedDelay`: stamps from the finish, so a long run drifts the cadence.

## Reopen when

- A job holds its thread long enough to starve the heartbeat.

## Observable change

- A job fires on time, not up to a second late; run now is immediate.
- `job.every()` is read after a run, not before it; a failed read no longer skips that run.
- During a run, its next run reads "now" until it ends.
