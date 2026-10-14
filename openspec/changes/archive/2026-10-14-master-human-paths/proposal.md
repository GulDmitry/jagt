## Why

In `act` the Master shipped and deployed whatever passed its read, a change to authentication, secrets or retention
included ([0042](../../../../docs/decisions/0042-a-path-list-keeps-a-step-with-the-human.md)).

## What Changes

- A project's `humanPaths` globs keep ship and deploy with the human where the task changes a path they name, or its
  paths cannot be read; the `master` window names the path.

## Capabilities

### Modified Capabilities

- `master`: a path kept for you keeps the press yours.
