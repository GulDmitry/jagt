## Why

A capability the board cannot name in `help` is a bug: adding a project was a form only, and the path was typed whole.

## What Changes

- `project` is a command: typed alone it opens the form, `project <path>` adds the repository with what its reading suggests.
- The path field offers the folders under what is typed, repositories first; hidden ones only after a dot.

## Capabilities

### Modified Capabilities

- `project-setup`: the verb `project` and the folders offered under the typed path.
