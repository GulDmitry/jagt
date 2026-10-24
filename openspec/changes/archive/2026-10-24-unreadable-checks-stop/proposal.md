## Why

On a live stand run the review poll's read answered `pipelineStatus: unknown` eight times in 15 minutes: the task sat
at CI_POLLING, its checks "unknown", "waiting", and nobody was told.

## What Changes

- A read that cannot list the pipelines gives the listing tool's own words for why, in `pipelineFailure`.
- The task keeps those reads running; a read that lists the checks forgets them.
- The third running since the last `lift` stops the task for the human, its evidence the reads' causes.

## Capabilities

### New Capabilities

### Modified Capabilities

- `review`: a pipeline jagt cannot read reaches an ending.
