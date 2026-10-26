## Why

A Master deploy of a task already on dev threw out of `MasterPress` into the job runner, which logged ERROR and
skipped the rest of the tick; the human's same press is a refusal. A refusal is an outcome, not a fault.

## What Changes

- `MasterPress` takes the refusals the board takes (`IllegalArgumentException`, `IllegalStateException`) as a WARN and a `false` press.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: a press the machine refuses is a WARN, not a job failure.
