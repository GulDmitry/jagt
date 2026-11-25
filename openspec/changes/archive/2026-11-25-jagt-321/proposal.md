## Why

Specs kept in another repository, under a folder other than `openspec/`, with change folders named after the key in
upper case, were never seen: the hand-back gate and the ship's fold did not fire.

## What Changes

- A project may name its spec root: a path inside a repository, and the project whose repository holds it.
- A change matches the task's key in any case, with or without a slug.
- A task holding no worktree of the named project is told to ask for it.

## Capabilities

### New Capabilities

### Modified Capabilities

- `spec-changes`: the specs are read where the project names them, and a change name matches in any case.
