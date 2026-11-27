## Why

The model check found a session's `SHIPPING` report landing on a `DEPLOYED` task, and no scenario said whether that is meant.

## What Changes

- A `DEPLOYED` task's session reporting `SHIPPING` lands: it keeps working after the deploy, and a refusal errors every call it makes.

## Capabilities

### Modified Capabilities

- `task-flow`: door two names the report after the deploy.
