## Why
Every job tick spawned `tmux list-windows` and `pgrep` once per task, so the spawns grew with the tasks, every 4 seconds.

## What Changes
- A job run lists the session host's windows once and reads the process table once, for every task it visits.
- A window opened, closed or pressed makes the next read inside the run list again.

## Capabilities

### Modified Capabilities

- `runtime`: a job run reads windows once.
