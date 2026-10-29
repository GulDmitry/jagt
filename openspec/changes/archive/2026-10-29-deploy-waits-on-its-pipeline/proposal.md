## Why

A deploy whose pipeline failed was reported to the session as landed, and the session could not see a defect that only exists in the merge result.

## What Changes

- jagt reads the host's pipeline on each deploy commit, and asks the session only once it is green.
- Until then the task's line says it waits on its pipeline.
- A deploy pipeline red on infrastructure is retried as a request's is.

## Capabilities

### Modified Capabilities

- `infra-wait`: a deploy stands on its pipeline, and its retry is the request's.
