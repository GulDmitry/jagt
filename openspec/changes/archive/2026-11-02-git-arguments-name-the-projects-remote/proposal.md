## Why

A base branch was stripped of `origin/` only, so a project sharing through another remote built `upstream/upstream/main`,
and the resume brief and the push hints named `origin`.

## What Changes

- The target and the base branch drop the project's own remote prefix.
- The resume brief, the tool gate's hints and a probe's refusal name the project's remote.

## Capabilities

### Modified Capabilities

- `task-start`: the remote a project names reaches every branch name and push hint.
