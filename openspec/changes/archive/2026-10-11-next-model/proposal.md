## Why

A fresh session always ran the CLI default, though only the agent knows how heavy the work ahead is.

## What Changes

- A fresh start runs the model named by `next-model: <name>` in `task_notes.md`.
- A name outside the runtime's `models()` refuses the hand-back as `validation`.

## Capabilities

### Modified Capabilities

- `runtime`: a fresh start takes its model from the notes.
