## Why

A failed read answered as a fact: an unreadable worktree pointer counted an orphan, a transient jar read raised the rebuilt banner.

## What Changes

- A worktree folder whose `.git` pointer cannot be read is not listed as an orphan.
- A running jar whose stamp cannot be read, other than by its absence, raises no rebuilt banner.
- A catch names its typed failure; `broad-catches` and `silent-catches` only fall (decision 0250).

## Capabilities

### New Capabilities

### Modified Capabilities

- `board-judgement`: an unreadable pointer is not an orphan.
- `supervisor`: an unreadable jar raises no rebuilt banner.
