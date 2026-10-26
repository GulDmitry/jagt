## Why

A session at `VERIFYING` that only asked or answered a question over an unchanged tree had its probes re-run and the
identical failure relayed again, three times; the Master's answer was dropped and the rounds ceiling stopped the task.

## What Changes

- A report that asks lands where a question lands, never at `VERIFYING`, and a question standing there runs nothing.
- A verification is keyed on the worktree's tree state and the declared probes; the key of the last failing run is
  kept on the task's turn, and an unchanged key runs and relays nothing: the task goes to the human with the failure noted.

## Capabilities

### New Capabilities

### Modified Capabilities

- `acceptance-probes`: a question is not verified; an unchanged tree is not verified twice.
