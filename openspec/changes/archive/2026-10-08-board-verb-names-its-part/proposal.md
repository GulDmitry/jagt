## Why

The palette branched on the ids `do`, `resume` and `help`, and two launches had endpoints of their own.

## What Changes

- A `GlobalCommand` names the board part it opens typed alone (`do` the launch row, `help` the legend).
- `do` and `resume` run through `POST /api/commands/{id}`; a launch creating no task is a refusal.

## Capabilities

### Modified Capabilities

- `board`: verbs declare what the board maps; no command keeps an endpoint of its own.
