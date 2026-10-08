## Why

`BoardWriteFilter` judged the raw path: `POST /api;x/tasks/actions/deploy` and `/%61pi/…` reached the controller
with no Origin.

## What Changes

- Every write carries the page's Origin, else 403; exempt only `/mcp` and `/api/agent/…` spelled as routed.

## Capabilities

### Modified Capabilities

- `board`: a write is the page's, however its path is spelled.
