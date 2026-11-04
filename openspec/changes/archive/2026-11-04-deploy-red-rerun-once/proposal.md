## Why

A deploy pipeline red on jobs the task never touched, or on a flaky one, is handed to the session as a conflict it has nothing to stage in:
the deploy never finishes.

## What Changes

- jagt re-runs a deploy pipeline red on the code once per pushed commit before handing it back; still red, it is handed back as before.
- The re-run is recorded on the deployment's target, so a restart never sends it again.

## Capabilities

### Modified Capabilities

- `deploy-check`: a pipeline red on the code is re-run once before it goes to the session.
