## Why

The Master's log, the session log and the `tail` report were oldest-first blobs: no gap, no colour, the newest line out of sight.

## What Changes

- One `LogLines` component lists every log view: newest entry on top, a gap between entries, the time muted, a failure in `--danger`.
- The role log keeps the reader's place when entries arrive; the legend gains a muted-time row and a danger-entry row.
- A role log line carries its level.

## Capabilities

### New Capabilities

### Modified Capabilities

- `board`: a role's log and the session log list newest first, coloured by level; the legend says so.
- `board-judgement`: the `tail` report lists newest first.
