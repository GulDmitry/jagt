## Why

An unreadable tmux window list must not read as a silent or gone agent, nor fail a job.

## What Changes

- A job or the watchdog reading an unreadable list decides nothing that round; a pressed focus is refused with the cause.

## Capabilities

### Modified Capabilities

- `runtime`: the one-name-one-window requirement names the reader's side.
