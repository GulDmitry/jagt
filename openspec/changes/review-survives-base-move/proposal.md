## Why

A rebase or merge of the base restamps a handed-back round, so the Master read it again and paid for the same diff twice.

## What Changes

- A verdict stores an identifier of the task's own diff, stable across a move of the base.
- A round whose own diff has that identifier keeps the previous verdict; any other change is read as before.

## Capabilities

### Modified Capabilities

- `master`: a round whose own diff did not change keeps its verdict.
