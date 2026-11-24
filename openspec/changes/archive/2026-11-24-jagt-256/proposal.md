## Why

Every task's worktree of one repository got the same Compose project name, so two tasks shared one database container and volume.

## What Changes

- A task's session, and a probe run in its worktree, start with `COMPOSE_PROJECT_NAME=<repository>-<task>`, lower-case and Compose-safe.
- Closing a task takes down that project by name, besides every project whose compose file lies in the worktree.

## Capabilities

### New Capabilities

### Modified Capabilities

- `platform`: a worktree's container stack is named after its repository and task.
