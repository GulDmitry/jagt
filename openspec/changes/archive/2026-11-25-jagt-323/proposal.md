## Why

An existing suite keeps its index, steps and flows where it already has them, and the only way to tell acceptance was to
override the whole workspace step of the brief, which then drifts from the shipped one.

## What Changes

- `workspace.index`, `scenarios`, `steps` and `flows` in `roles/acceptance/role.yml` place each, relative to the workspace.
- The brief names them as `{workspace.index}`, `{workspace.scenarios}`, `{workspace.steps}` and `{workspace.flows}`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `acceptance-workspace`: the workspace's layout is the role folder's, named in the brief.
