## Why

CI went red after a local green in 12 of 28 runs since 2026-11-20: the local CI ran other commands than CI's jobs,
and CI's time ceilings and timing gate stopped runs that a shared runner made slow.

## What Changes

- Each CI job's work is one `gate<Job>` Gradle task; the workflows and `ci-local.sh` call it and no other task.
- `ci-local.sh` runs every pull request job: each Linux one in a container with a Docker engine of its own, the macOS install natively.
- `-Ptimed=false`, set by both CI engines, drops every time limit and time record; a job's timeout is its only stop.
- CI keeps no stand timing history, runs no timing gate and no nightly delay job.

## Capabilities

### New Capabilities

### Modified Capabilities

- `engineering`: a run's limit holds only when timed; the board's CI steps are wired.
- `fitness`: a workflow runs a gate task and nothing else.
- `stand-engines`: the timing trend is not kept on CI.
