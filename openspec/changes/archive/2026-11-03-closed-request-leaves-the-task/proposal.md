## Why

A request the human closed on the host, unmerged, kept its repository in a task spanning several: every sweep called it
gone and nothing acted, then `deploy` merged that repository with the rest.

## What Changes

- Where a task holds several repositories and the host says one request was closed unmerged, that repository leaves the
  task: its checkout goes, its branch stays, the session is told, and the sweep judges the others in the same pass.
- A task's only repository, and a request the host says does not exist, stay gone as before.

## Capabilities

### New Capabilities

- `task-flow`: a closed request takes its repository out of a task spanning several.
