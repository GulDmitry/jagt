# 0263 — A job reports a failure it handled through JobFaults

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md) ·
on [0250](0250-a-catch-names-its-failure-and-reports-it.md), [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-27.

## Context

- `Jobs` booked a job as failed only when its `run()` threw; a catch inside a job, or a failed answer a job read, left the job clean and the head's
  jobs chip silent.
- Twelve job paths handled a failure that way.

## Decision

- **Port `JobFaults.faulted(cause)`** books a handled failure against the job running on the calling thread; `carry` hands the booking to another
  thread. `Jobs` reports the first cause of a run until the job's next run begins.
- **A job reaches it through a component it already holds**, never a new field: `DeployFixWorktrees`, `CheckerReads`, `ReviewReads`.
- **Row `job-catches-report`** (`JobCatchFitness`, `job/` main code) counts a broad catch with neither `faulted(` nor a `throw`, max 0.
- **Row `ports`** rises from 58 to 59 for this port.

## Rejected

- `Jobs.faulted(id, cause)`: every job would hold `Jobs`, which holds every job.
- One id per site: a helper used by two jobs would need to know which one runs it.

## Reopen when

- A job runs its work on a thread nothing carried: its failure is booked against no job.
