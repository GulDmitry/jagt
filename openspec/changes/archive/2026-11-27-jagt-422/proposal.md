## Why

A failure a job handled inside its own run never reached `Jobs`, so the job counted as clean and the head's jobs chip stayed silent.

## What Changes

- A job reports a handled failure through `JobFaults.faulted(cause)`; `Jobs` counts that job as failed with the first cause of its run.
- The failure stands until the job's next run begins; a run that books nothing ends clean.
- A catch of a broad exception in `job/` that neither reports nor rethrows fails the fitness `job-catches-report`.

## Capabilities

### Modified Capabilities

- `unattended-work`: a handled failure counts as the job's failure.
