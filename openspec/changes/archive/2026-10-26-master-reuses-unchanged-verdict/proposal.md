## Why

Every new turn over one unchanged diff, a `no_changes` or a question-only hand-back, asked two model roles again: 17
Master reviews in a morning over the same diff. A verdict was kept by the turn it answered, and only a ready one.

## What Changes

- A verdict, ready or not ready, is stored with the kind of the hand-back it read and the task's own diff identifier.
- A hand-back with the same own diff and the same kind reuses it; any other input asks anew.
- A verdict stored without the kind matches no hand-back.

## Capabilities

### Modified Capabilities

- `master`: the kept verdict is keyed on its inputs, not the turn.
