## Why

A human who knows a task needs another repository had to wait for its session to ask, or tell the session in prose.

## What Changes

- `add-repo <ticket> [project]` typed in the board's palette names the project; the task need not have asked.
- A project not in `jagt.yml`, one the task already works in, and a status that cannot take a repository are refused with nothing changed.
- The add-repo requirement moves from `git` to its own `repo-attach` spec, which keeps `git` under its line budget.

## Capabilities

### New Capabilities

- `repo-attach`: a running task gains a repository, asked for or named.

### Modified Capabilities

- `git`: the add-repo requirement leaves.
- `board`: add-repo is typed with a project, with no control of its own.
