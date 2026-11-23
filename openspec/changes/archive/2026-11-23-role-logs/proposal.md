## Why

The Master's steps were seen in one standing tmux window; acceptance and triage had no feed. One feed for every model role, on the board, needs no window.

## What Changes

- Every running model role has a chip in the head and a feed (one logback appender per role module); the chip opens its log in the pane and hides it.
- `MasterFeedWindow` and the standing `master` tmux window are removed.

## Capabilities

### New Capabilities

### Modified Capabilities

- `board`: a running model role's chip opens its log in the pane; `role-log` joins the push stream.
- `unattended-work`: the Master's steps are seen in its log on the board, in no tmux window.
