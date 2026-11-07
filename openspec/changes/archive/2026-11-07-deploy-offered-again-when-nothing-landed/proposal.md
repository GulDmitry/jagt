## Why

A closed request for the repository a deploy's merge conflicted on, with nothing landed, left the task in
`DEPLOY_CONFLICT` with nothing to resolve.

## What Changes

- With no repository landed, the conflicted repository leaving returns the task to `REVIEWED`, `deploy` offered again.
- Where one landed, the task stays `DEPLOY_CONFLICT` or lands as before; `revert` is unchanged.

## Capabilities

### Modified Capabilities

- `task-flow`: the repository a deploy conflicted on leaving, with nothing landed, returns the task to where `deploy` is offered.
