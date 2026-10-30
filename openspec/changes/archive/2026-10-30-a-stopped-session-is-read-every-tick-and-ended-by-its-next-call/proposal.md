## Why

A stop was stamped by the watchdog beside the session record, so a call from the worktree and the next tick could disagree, and a prompt that blocked a session no longer pinged the human.

## What Changes

- The watchdog reads every open task each tick and the reducer alone records the stop; a call from the worktree ends a stop of any kind.
- A prompt that blocks a session pings the human once with its cause.
- A permission the session reported itself is never cleared by work starting again.

## Capabilities

### Modified Capabilities

- `attention`: silence is read by the watchdog and recorded by the session record.
