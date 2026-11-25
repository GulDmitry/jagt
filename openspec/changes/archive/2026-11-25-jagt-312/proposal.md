## Why

On a board with no tasks, an open role log sat below the task filters and a key-hint box, its empty line unstyled: the pane read as broken.

## What Changes

- A role's empty log says so through `LogLines`, where its first entry will stand.
- With no task in view the pane stands alone: no task list, no key hints.
- An element the board hides stays hidden whatever display a rule gives it, so the phase filters leave an empty board.

## Capabilities

### New Capabilities

### Modified Capabilities

- `board`: a role's empty log on an empty board stands alone where its first entry will.
