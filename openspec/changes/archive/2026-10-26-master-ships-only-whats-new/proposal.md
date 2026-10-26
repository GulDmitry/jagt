## Why
A branch stays ahead of its target after the first push, so every hand-back, even one that changed nothing, was
shipped again; the session found nothing to push and the round came back, five times on one task.

## What Changes
- The Master's ship is due only on something new: uncommitted changes, a HEAD its remote branch lacks, or drafted replies.

## Capabilities

### Modified Capabilities

- `master`: a hand-back with nothing new is not shipped.
