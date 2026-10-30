## Why

`Watch.defaults()` admitted every stop kind, so a job that named none, such as the nudge resubmit, ran on a stopped task, a gate nobody wrote.

## What Changes

- `Watch.defaults()` admits `NONE` alone; a job on a stopped task names the stop kind it ends.
- The merged-request read, the ticket prefetch and the launch read name all kinds, as before.

## Capabilities

### Modified Capabilities

- `unattended-work`: a stopped task reaches only the job that names its stop kind.
