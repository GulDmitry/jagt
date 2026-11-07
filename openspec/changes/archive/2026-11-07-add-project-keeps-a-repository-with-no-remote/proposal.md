## Why

A repository with no remote at all is refused by add a project, though `remote: none` keeps one on this machine.

## What Changes

- A repository listing no remote is read from its local branches and saved with `remote: none`; the form gains nothing.

## Capabilities

### Modified Capabilities

- `project-setup`: add a project accepts a repository with no remote.
